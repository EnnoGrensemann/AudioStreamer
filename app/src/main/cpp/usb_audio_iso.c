#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <poll.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>
#include <android/log.h>

#define TAG "UsbAudioIso"
#define N_URBS 8
#define PACKETS_PER_URB 16

typedef struct {
    int fd;
    int endpoint;
    int max_packet_size;
    int running;
    struct usbdevfs_urb *urbs[N_URBS];
    unsigned char *buffers[N_URBS];
} iso_ctx_t;

static iso_ctx_t *g_ctx = NULL;

JNIEXPORT jint JNICALL
Java_com_vinylcast_audio_UsbIsoNative_startIsoCapture(
        JNIEnv *env,
        jobject thiz,
        jint fd,
        jint endpoint_addr,
        jint max_packet_size,
        jint subframe_size,
        jobject callback_obj) {

    if (g_ctx != NULL) {
        return -1;
    }

    jclass cls = (*env)->GetObjectClass(env, callback_obj);
    jmethodID onPcmMethod = (*env)->GetMethodID(env, cls, "onPcmData", "([BI)V");
    if (!onPcmMethod) {
        (*env)->ExceptionClear(env);
        __android_log_print(ANDROID_LOG_ERROR, TAG, "onPcmData method not found");
        return -2;
    }

    iso_ctx_t *ctx = (iso_ctx_t *) calloc(1, sizeof(iso_ctx_t));
    if (!ctx) return -3;

    ctx->fd = fd;
    ctx->endpoint = endpoint_addr;
    ctx->max_packet_size = max_packet_size;
    ctx->running = 1;
    g_ctx = ctx;

    size_t urb_struct_size = sizeof(struct usbdevfs_urb) +
                             PACKETS_PER_URB * sizeof(struct usbdevfs_iso_packet_desc);
    size_t buf_size = (size_t) PACKETS_PER_URB * (size_t) max_packet_size;

    int submitted = 0;
    for (int i = 0; i < N_URBS; i++) {
        ctx->urbs[i] = (struct usbdevfs_urb *) calloc(1, urb_struct_size);
        ctx->buffers[i] = (unsigned char *) calloc(1, buf_size);
        if (!ctx->urbs[i] || !ctx->buffers[i]) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "OOM allocating URB %d", i);
            break;
        }

        struct usbdevfs_urb *urb = ctx->urbs[i];
        urb->type = USBDEVFS_URB_TYPE_ISO;
        urb->endpoint = (unsigned char) endpoint_addr;
        urb->flags = USBDEVFS_URB_ISO_ASAP;
        urb->buffer = ctx->buffers[i];
        urb->buffer_length = (int) buf_size;
        urb->number_of_packets = PACKETS_PER_URB;
        for (int p = 0; p < PACKETS_PER_URB; p++) {
            urb->iso_frame_desc[p].length = (unsigned int) max_packet_size;
        }

        if (ioctl(fd, USBDEVFS_SUBMITURB, urb) < 0) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "SUBMITURB %d failed: %s", i, strerror(errno));
        } else {
            submitted++;
        }
    }

    if (submitted == 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "No URBs submitted");
        for (int i = 0; i < N_URBS; i++) {
            free(ctx->urbs[i]);
            free(ctx->buffers[i]);
        }
        free(ctx);
        g_ctx = NULL;
        return -4;
    }

    unsigned char *pcm_out = (unsigned char *) malloc(buf_size);
    jbyteArray j_pcm = (*env)->NewByteArray(env, (jsize) buf_size);
    if (!pcm_out || !j_pcm) {
        free(pcm_out);
        g_ctx = NULL;
        return -5;
    }

    __android_log_print(ANDROID_LOG_INFO, TAG, "Iso capture started: ep=0x%02x maxPkt=%d sub=%d",
                        endpoint_addr, max_packet_size, subframe_size);

    struct pollfd pfd;
    pfd.fd = fd;
    pfd.events = POLLOUT; // usbfs signals completed URBs via POLLOUT

    while (ctx->running) {
        int pret = poll(&pfd, 1, 200);
        if (pret < 0) {
            if (errno == EINTR) continue;
            __android_log_print(ANDROID_LOG_ERROR, TAG, "poll error: %s", strerror(errno));
            break;
        }
        if (pret == 0) {
            continue;
        }

        while (ctx->running) {
            struct usbdevfs_urb *reaped = NULL;
            if (ioctl(fd, USBDEVFS_REAPURBNDELAY, &reaped) < 0) {
                if (errno == EAGAIN) break;
                if (errno == ENODEV || errno == ESHUTDOWN) {
                    ctx->running = 0;
                    break;
                }
                __android_log_print(ANDROID_LOG_WARN, TAG, "REAPURB error: %s", strerror(errno));
                break;
            }
            if (!reaped) break;

            int out_len = 0;
            unsigned char *src = (unsigned char *) reaped->buffer;
            for (int p = 0; p < reaped->number_of_packets; p++) {
                unsigned int actual = reaped->iso_frame_desc[p].actual_length;
                int status = reaped->iso_frame_desc[p].status;
                if (status == 0 && actual > 0) {
                    unsigned char *pkt = src + (p * max_packet_size);
                    if (subframe_size == 3) {
                        // Convert 24-bit LE PCM -> 16-bit LE PCM (take upper 2 bytes)
                        for (unsigned int b = 0; b + 2 < actual; b += 3) {
                            pcm_out[out_len++] = pkt[b + 1];
                            pcm_out[out_len++] = pkt[b + 2];
                        }
                    } else {
                        memcpy(pcm_out + out_len, pkt, actual);
                        out_len += (int) actual;
                    }
                }
                reaped->iso_frame_desc[p].length = (unsigned int) max_packet_size;
                reaped->iso_frame_desc[p].actual_length = 0;
                reaped->iso_frame_desc[p].status = 0;
            }

            if (out_len > 0) {
                (*env)->SetByteArrayRegion(env, j_pcm, 0, out_len, (jbyte *) pcm_out);
                (*env)->CallVoidMethod(env, callback_obj, onPcmMethod, j_pcm, out_len);
                if ((*env)->ExceptionCheck(env)) {
                    (*env)->ExceptionClear(env);
                    ctx->running = 0;
                }
            }

            if (ctx->running) {
                if (ioctl(fd, USBDEVFS_SUBMITURB, reaped) < 0) {
                    __android_log_print(ANDROID_LOG_ERROR, TAG, "Resubmit URB failed: %s", strerror(errno));
                    ctx->running = 0;
                }
            }
        }
    }

    for (int i = 0; i < N_URBS; i++) {
        if (ctx->urbs[i]) {
            ioctl(fd, USBDEVFS_DISCARDURB, ctx->urbs[i]);
            struct usbdevfs_urb *reaped = NULL;
            ioctl(fd, USBDEVFS_REAPURBNDELAY, &reaped);
            free(ctx->urbs[i]);
        }
        free(ctx->buffers[i]);
    }

    free(pcm_out);
    (*env)->DeleteLocalRef(env, j_pcm);
    free(ctx);
    g_ctx = NULL;
    __android_log_print(ANDROID_LOG_INFO, TAG, "Iso capture stopped");
    return 0;
}

JNIEXPORT void JNICALL
Java_com_vinylcast_audio_UsbIsoNative_stopIsoCapture(JNIEnv *env, jobject thiz) {
    if (g_ctx != NULL) {
        g_ctx->running = 0;
    }
}

cmake_minimum_required(VERSION 3.22.1)
project("vinylcast_usb" C)

add_library(vinylcast_usb SHARED usb_audio_iso.c)

find_library(log-lib log)
target_link_libraries(vinylcast_usb ${log-lib})

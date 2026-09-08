LOCAL_PATH := $(call my-dir)
MY_LOCAL_PATH := $(LOCAL_PATH)

include $(MY_LOCAL_PATH)/cloak/Android.mk

# 1. Include the upstream hev-socks5-tunnel
# This will define modules: libyaml, liblwip, libhev-task-system, and hev-socks5-tunnel (static)
include $(MY_LOCAL_PATH)/third_party/hev-socks5-tunnel/Android.mk

# 2. Define the main JNI module
LOCAL_PATH := $(MY_LOCAL_PATH)
include $(CLEAR_VARS)

LOCAL_MODULE := hev-tun2socks-jni
LOCAL_SRC_FILES := hev_tun2socks_jni.c

HEV_SOCKS5_TUNNEL_PATH := $(LOCAL_PATH)/third_party/hev-socks5-tunnel

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH) \
    $(HEV_SOCKS5_TUNNEL_PATH)/src \
    $(HEV_SOCKS5_TUNNEL_PATH)/src/misc \
    $(HEV_SOCKS5_TUNNEL_PATH)/src/core/include

LOCAL_CFLAGS := -DFD_SET_DEFINED -DSOCKLEN_T_DEFINED -DENABLE_LIBRARY
LOCAL_CFLAGS += -DCOMMIT_ID=\"9a06bc6\"

LOCAL_STATIC_LIBRARIES := hev-socks5-tunnel libyaml liblwip libhev-task-system
LOCAL_LDLIBS := -llog

# Page size support
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384
LOCAL_LDFLAGS += -Wl,-z,common-page-size=16384

include $(BUILD_SHARED_LIBRARY)

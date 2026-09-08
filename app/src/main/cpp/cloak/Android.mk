LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := cloak
LOCAL_SRC_FILES := cloak.c
LOCAL_CFLAGS := -O2 -Wall -D__ANDROID__ -Wno-unused-parameter -Wno-deprecated-declarations
LOCAL_LDLIBS := -llog
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384
LOCAL_LDFLAGS += -Wl,-z,common-page-size=16384
include $(BUILD_SHARED_LIBRARY)

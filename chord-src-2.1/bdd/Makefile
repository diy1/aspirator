# Things you may need to change, or redefine on the command line:
#   JDK_ROOT     -- location where you installed JDK
#   M32_FLAG     -- "-m32" if using 32-bit JVM on 64-bit machine

BUDDY_SRC = buddy/src
DIST_DIR = ..
ifdef JDK_ROOT
	JNI_H_DIR = $(shell find $(JDK_ROOT) -name jni.h -exec dirname '{}' \;)
	INCLUDES = -I. -I$(BUDDY_SRC) -I$(BUDDY_SRC)/.. -I$(JNI_H_DIR)
	JNI_MD_H_DIR = $(shell find $(JDK_ROOT) -name jni_md.h -exec dirname '{}' \;)
	ifneq (x$(JNI_MD_H_DIR),x)
		INCLUDES += -I$(JNI_MD_H_DIR)
	endif
endif

ifeq (${OS},Windows_NT)
  CFLAGS = $(M32_FLAG) -fPIC -Wall -O2 -mno-cygwin
  LINK = dllwrap
  LINKFLAGS = $(M32_FLAG) -s --target=i386-mingw32 --add-underscore --driver-name gcc -mno-cygwin
  BUDDY_LIB_NAME = $(DIST_DIR)/buddy.dll
else
  CFLAGS = $(M32_FLAG) -fPIC -DSPECIALIZE_RELPROD -DSPECIALIZE_AND -DSPECIALIZE_OR -DSMALL_NODES -O2 -fomit-frame-pointer
  LINK = $(CC)
  ifeq (${shell uname -s},Darwin)
    LINKFLAGS = $(M32_FLAG) -lm -dynamiclib -framework JavaVM -single_module -undefined suppress -flat_namespace
    BUDDY_LIB_NAME = $(DIST_DIR)/libbuddy.dylib
  else
    LINKFLAGS = $(M32_FLAG) -lm -shared
    BUDDY_LIB_NAME = $(DIST_DIR)/libbuddy.so
  endif
endif

BUDDY_H = buddy_jni.h
BUDDY_SRCS = buddy_jni.c \
	$(BUDDY_SRC)/bddio.c $(BUDDY_SRC)/bddop.c $(BUDDY_SRC)/bvec.c \
	$(BUDDY_SRC)/cache.c $(BUDDY_SRC)/fdd.c $(BUDDY_SRC)/imatrix.c \
	$(BUDDY_SRC)/kernel.c $(BUDDY_SRC)/pairs.c $(BUDDY_SRC)/prime.c \
	$(BUDDY_SRC)/reorder.c $(BUDDY_SRC)/tree.c $(BUDDY_SRC)/trace.c
BUDDY_OBJS = $(BUDDY_SRCS:.c=.o)

default: $(BUDDY_LIB_NAME)

$(BUDDY_LIB_NAME): $(BUDDY_OBJS)
	$(LINK) -o $@ $(BUDDY_OBJS) $(LINKFLAGS)

buddy_jni.o: $(BUDDY_H)

.c.o:
	$(CC) $(CFLAGS) $(INCLUDES) -c -o $@ $<

clean:
	$(RM) -f $(BUDDY_OBJS) $(BUDDY_LIB_NAME)

empty := 
space := $(empty) $(empty)

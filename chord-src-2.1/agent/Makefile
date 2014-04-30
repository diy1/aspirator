# Things you may need to change, or redefine on the command line:
#   JDK_ROOT    -- location where you installed JDK
#   M32_FLAG    -- "-m32" if using 32-bit JVM on 64-bit machine

DIST_DIR = ..
INSTR_LIB_NAME = $(DIST_DIR)/libchord_instr_agent.so
ifdef JDK_ROOT
	JNI_H_DIR = $(shell find $(JDK_ROOT) -name jni.h -exec dirname '{}' \;)
	INCLUDES = -I. -I$(BUDDY_SRC) -I$(BUDDY_SRC)/.. -I$(JNI_H_DIR)
	JNI_MD_H_DIR = $(shell find $(JDK_ROOT) -name jni_md.h -exec dirname '{}' \;)
	ifneq (x$(JNI_MD_H_DIR),x)
		INCLUDES += -I$(JNI_MD_H_DIR)
	endif
endif

ifeq (${OS},Windows_NT)
  CXXFLAGS = $(M32_FLAG) -mno-cygwin -Wall -O2
  LINK = dllwrap
  LINKFLAGS = $(M32_FLAG) -s --target=i386-mingw32 --add-underscore --driver-name c++ -mno-cygwin
else
  CXXFLAGS = $(M32_FLAG) -fPIC -O2 -fomit-frame-pointer
  LINK = $(CXX) 
  ifeq (${shell uname -s},Darwin)
    LINKFLAGS = $(M32_FLAG) -dynamiclib -framework JavaVM  -single_module -undefined suppress -flat_namespace
  else
    LINKFLAGS = $(M32_FLAG) -shared
  endif
endif

default: $(INSTR_LIB_NAME)

$(INSTR_LIB_NAME): chord_instr_agent.o
	$(LINK) $(LINKFLAGS) $< -o $@

chord_instr_agent.o: chord_instr_agent.cpp
	$(CXX) chord_instr_agent.cpp $(CXXFLAGS) $(INCLUDES) -c -o $@

clean:
	$(RM) -f chord_instr_agent.o $(INSTR_LIB_NAME)


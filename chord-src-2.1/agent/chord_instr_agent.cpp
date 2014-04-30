/*
 * This agent expects at least one of the following arguments:
 *
 * 1. event_handler_class=<CLASS NAME>
 *    If this argument is present, then this agent takes the following actions:
 *    a) When the VMInit event is called by JVMTI, this agent calls the static
 *       synchronized method "void init(String)" in the class denoted by
 *       <CLASS NAME>; also, all agent options are passed via the string
 *       argument to that method.
 *    b) When the VMDeath event is called by JVMTI, this agent calls the static
 *       synchronized method "void done()" in the class denoted by <CLASS NAME>.
 *
 * 2. classes_file=<FILE NAME>
 *    If this argument is present, then this agent collects all loaded classes
 *    and writes their names, one per line, to the file denoted by <FILE NAME>.
 */
#include <jvmti.h>
#include <assert.h>
#include <stdlib.h>
#include <string.h>
using namespace std;
#include "iostream"
#include "fstream"
#include "set"
#include "map"
#include "vector"
#include "string"

static jvmtiEnv* jvmti_env;

#define MAX 20000

static bool list_loaded_classes = false;
static char classes_file[MAX];

static bool enable_event_handler = false;
static char event_handler_class[MAX];
static char* event_handler_args_str = NULL;

char* get_token(char *str, char *seps, char *buf, int max)
{
    int len;
    buf[0] = 0;
    if (str == NULL || str[0] == 0)
        return NULL;
    str += strspn(str, seps);
    if (str[0] == 0)
        return NULL;
    len = (int) strcspn(str, seps);
    if (len >= max) {
		cerr << "ERROR: get_token failed" << endl;
		exit(1);
    }
    strncpy(buf, str, len);
    buf[len] = 0;
    return str + len;
}

static void call_event_handler_class_method(JNIEnv* jni_env,
	const char* mName, const char* mSign, const char* args)
{
	jclass c = jni_env->FindClass(event_handler_class);
	if (c == NULL) {
		cout << "ERROR: JNI: Cannot find class: " <<
			event_handler_class << endl;
		exit(1);
	}
	jmethodID m = jni_env->GetStaticMethodID(c, mName, mSign);
	if (m == NULL) {
		cout << "ERROR: JNI: Cannot get method " << mName << mSign <<
			" from class: " << event_handler_class << endl;
		exit(1);
	}
	if (args != NULL) {
		jstring a = jni_env->NewStringUTF(args);
		jni_env->CallStaticObjectMethod(c, m, a);
	} else
		jni_env->CallStaticObjectMethod(c, m);
}

static void JNICALL VMStart(jvmtiEnv *jvmti_env, JNIEnv* jni_env)
{
    cout << "ENTER VMStart" << endl;
    cout << "LEAVE VMStart" << endl;
}

static void JNICALL VMInit(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread)
{
    cout << "ENTER VMInit" << endl;

	if (enable_event_handler) {
		const char* mName = "init";
		const char* mSign = "(Ljava/lang/String;)V";
		call_event_handler_class_method(jni_env, mName, mSign, event_handler_args_str);
	}

	cout << "LEAVE VMInit" << endl;
}

static void JNICALL VMDeath(jvmtiEnv *jvmti_env, JNIEnv* jni_env)
{
    cout << "ENTER VMDeath" << endl;

	if (list_loaded_classes) {
		jint class_count;
		jclass* classes;
		jvmtiError result;
		result = jvmti_env->GetLoadedClasses(&class_count, &classes);
		assert(result == JVMTI_ERROR_NONE);
		fstream classes_out;
 		classes_out.open(classes_file, ios::out);
		for (int i = 0; i < class_count; i++) {
			jclass klass = classes[i];
			char* class_name;
			jvmti_env->GetClassSignature(klass, &class_name, NULL);
			if (class_name[0] == '[')
				continue;
			classes_out << class_name << endl;
		}
		classes_out.close();
	}

	if (enable_event_handler) {
		const char* mName = "done";
		const char* mSign = "()V";
		call_event_handler_class_method(jni_env, mName, mSign, NULL);
	}

	cout << "LEAVE VMDeath" << endl;
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved)
{
    cout << "ENTER Agent_OnLoad" << endl;
	if (options == NULL) {
		cerr << "ERROR: Expected options to agent" << endl;
		exit(1);
	}
	char* next = options;
	while (1) {
    	char token[MAX];
		next = get_token(next, (char*) ",=", token, sizeof(token));
		if (next == NULL)
			break;
        if (strcmp(token, "event_handler_class") == 0) {
            next = get_token(next, (char*) ",=", event_handler_class, MAX);
            if (next == NULL) {
                cerr << "ERROR: Bad option event_handler_class=<name>: "
					<< options << endl;
				exit(1);
            }
			enable_event_handler = true;
			continue;
        }
		if (strcmp(token, "classes_file") == 0) {
            next = get_token(next, (char*) ",=", classes_file, MAX);
            if (next == NULL) {
                cerr << "ERROR: Bad option classes_file=<name>: "
					<< options << endl;
				exit(1);
            }
			list_loaded_classes = true;
			continue;
		}
	}
	if (enable_event_handler) {
		event_handler_args_str = strdup(options);
		assert(event_handler_args_str != NULL);
	}

    jvmtiError retval;

    jint result = jvm->GetEnv((void**) &jvmti_env, JVMTI_VERSION_1_0);
    assert(result == JNI_OK);

    retval = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE,
		JVMTI_EVENT_VM_START, NULL);
    assert(retval == JVMTI_ERROR_NONE);
    retval = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE,
		JVMTI_EVENT_VM_INIT, NULL);
    assert(retval == JVMTI_ERROR_NONE);
    retval = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE,
		JVMTI_EVENT_VM_DEATH, NULL);
    assert(retval == JVMTI_ERROR_NONE);

    jvmtiCapabilities capa;
    memset(&capa, 0, sizeof(capa));
    capa.can_tag_objects = 1;
    retval = jvmti_env->AddCapabilities(&capa);
    assert(retval == JVMTI_ERROR_NONE);

    jvmtiEventCallbacks callbacks;
    memset(&callbacks, 0, sizeof(callbacks));
    callbacks.VMStart = &VMStart;
    callbacks.VMInit = &VMInit;
    callbacks.VMDeath = &VMDeath;
    retval = jvmti_env->SetEventCallbacks(&callbacks, sizeof(callbacks));
    assert(retval == JVMTI_ERROR_NONE);

    cout << "LEAVE Agent_OnLoad" << endl;
	return JNI_OK;
}


JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *jvm)
{
    cout << "ENTER Agent_OnUnload" << endl;
    cout << "LEAVE Agent_OnUnload" << endl;
}


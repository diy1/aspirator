#include <jni.h>
#include <bdd.h>
#include <fdd.h>
#include <stdlib.h>
#include <time.h>

#include "buddy_jni.h"

/*
** When casting from `int' to a pointer type, you should
** first cast to `intptr_cast_type'.  This is a type
** that is (a) the same size as a pointer, on most platforms,
** to avoid compiler warnings about casts from pointer to int of
** different size; and (b) guaranteed to be at least as big as
** `int'.
*/
#include <inttypes.h>
#if INTPTR_MAX >= INT_MAX
  typedef intptr_t intptr_cast_type;
#else /* no intptr_t, or intptr_t smaller than `int' */
  typedef intmax_t intptr_cast_type;
#endif

#define INVALID_BDD -1

static int bdd_error;

static void bdd_errhandler(int errcode)
{
#if defined(TRACE_BUDDYLIB)
  printf("bdd_errstring(%d)\n", errcode);
#endif
  //printf("BuDDy error: %s\n", bdd_errstring(errcode));
  bdd_error = errcode;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_clear_error()\n");
#endif
  bdd_clear_error();
}

static int check_error(JNIEnv *env)
{
  int err = bdd_error;
  char* clsname;
  if (!err) return 0; // fast path
  clsname = NULL;
  switch (err) {
  case BDD_MEMORY:   /* Out of memory */
    clsname = "java/lang/OutOfMemoryError";
    break;
  case BDD_VAR:      /* Unknown variable */
  case BDD_RANGE:    /* Variable value out of range (not in domain) */
  case BDD_DEREF:    /* Removing external reference to unknown node */
  case BDD_RUNNING:  /* Called bdd_init() twice whithout bdd_done() */
  case BDD_ORDER:    /* Vars. not in order for vector based functions */
  case BDD_BREAK:    /* User called break */
  case BDD_VARNUM:  /* Different number of vars. for vector pair */
  case BDD_OP:      /* Unknown operator */
  case BDD_VARSET:  /* Illegal variable set */
  case BDD_VARBLK:  /* Bad variable block operation */
  case BDD_DECVNUM: /* Trying to decrease the number of variables */
  case BDD_REPLACE: /* Replacing to already existing variables */
  case BDD_NODENUM: /* Number of nodes reached user defined maximum */
  case BVEC_SIZE:    /* Mismatch in bitvector size */
  case BVEC_DIVZERO: /* Division by zero */
    clsname = "net/sf/javabdd/BDDException";
    break;
  case BDD_FILE:     /* Some file operation failed */
  case BDD_FORMAT:   /* Incorrect file format */
    clsname = "java/io/IOException";
    break;
  case BDD_NODES:   /* Tried to set max. number of nodes to be fewer */
                    /* than there already has been allocated */
  case BDD_ILLBDD:  /* Illegal bdd argument */
  case BDD_SIZE:    /* Illegal size argument */
  case BVEC_SHIFT:   /* Illegal shift-left/right parameter */
    //clsname = "java/lang/IllegalArgumentException";
    clsname = "net/sf/javabdd/BDDException";
    break;
  default:
    clsname = "java/lang/InternalError";
    break;
  }
  if (clsname != NULL) {
    jclass cls = (*env)->FindClass(env, clsname);
#if defined(TRACE_BUDDYLIB)
    printf("bdd_errstring(%d)\n", err);
#endif
    (*env)->ThrowNew(env, cls, bdd_errstring(err));
    (*env)->DeleteLocalRef(env, cls);
  }
  bdd_error = 0;
  return err;
}

static JNIEnv *jnienv;

static void bdd_gbchandler(int code, bddGbcStat *s)
{
  jclass bdd_cls, buddy_cls, gc_cls;
  jobject factory_obj, gc_obj;
  jfieldID fid, fid2;
  jmethodID mid;
  
  bdd_cls = (*jnienv)->FindClass(jnienv, "net/sf/javabdd/BDDFactory");
  buddy_cls = (*jnienv)->FindClass(jnienv, "net/sf/javabdd/BuDDyFactory");
  gc_cls = (*jnienv)->FindClass(jnienv, "net/sf/javabdd/BDDFactory$GCStats");
  if (!bdd_cls || !buddy_cls || !gc_cls) {
    return;
  }
  
  mid = (*jnienv)->GetStaticMethodID(jnienv, buddy_cls, "gc_callback", "(I)V");
  fid2 = (*jnienv)->GetStaticFieldID(jnienv, buddy_cls, "INSTANCE", "Lnet/sf/javabdd/BuDDyFactory;");
  fid = (*jnienv)->GetFieldID(jnienv, bdd_cls, "gcstats", "Lnet/sf/javabdd/BDDFactory$GCStats;");
  if (!mid || !fid2 || !fid) {
    return;
  }
  
  factory_obj = (*jnienv)->GetStaticObjectField(jnienv, buddy_cls, fid2);
  if (!factory_obj) {
    printf("Error: BuDDyFactory.INSTANCE is null\n");
    return;
  }
  
  gc_obj = (*jnienv)->GetObjectField(jnienv, factory_obj, fid);
  if (!gc_obj) {
    printf("Error: gcstats is null\n");
    return;
  }
  
  fid = (*jnienv)->GetFieldID(jnienv, gc_cls, "nodes", "I");
  if (fid) {
    (*jnienv)->SetIntField(jnienv, gc_obj, fid, s->nodes);
  }
  fid = (*jnienv)->GetFieldID(jnienv, gc_cls, "freenodes", "I");
  if (fid) {
    (*jnienv)->SetIntField(jnienv, gc_obj, fid, s->freenodes);
  }
  fid = (*jnienv)->GetFieldID(jnienv, gc_cls, "time", "J");
  if (fid) {
    long t = s->time;
    if (CLOCKS_PER_SEC < 1000) {
      t = t * 1000 / CLOCKS_PER_SEC;
    }
    else {
      t /= (CLOCKS_PER_SEC/1000);
    }
    (*jnienv)->SetLongField(jnienv, gc_obj, fid, t);
  }
  fid = (*jnienv)->GetFieldID(jnienv, gc_cls, "sumtime", "J");
  if (fid) {
    long t = s->sumtime;
    if (CLOCKS_PER_SEC < 1000) {
      t = t * 1000 / CLOCKS_PER_SEC;
    }
    else {
      t /= (CLOCKS_PER_SEC/1000);
    }
    (*jnienv)->SetLongField(jnienv, gc_obj, fid, t);
  }
  fid = (*jnienv)->GetFieldID(jnienv, gc_cls, "num", "I");
  if (fid) {
    (*jnienv)->SetIntField(jnienv, gc_obj, fid, s->num);
  }
  
  (*jnienv)->CallStaticVoidMethod(jnienv, buddy_cls, mid, code);
}

static void bdd_resizehandler(int a, int b)
{
  jclass cls = (*jnienv)->FindClass(jnienv, "net/sf/javabdd/BuDDyFactory");
  jmethodID mid = (*jnienv)->GetStaticMethodID(jnienv, cls, "resize_callback", "(II)V");
  if (mid == 0) {
    return;
  }
  (*jnienv)->CallStaticVoidMethod(jnienv, cls, mid, a, b);
}

static void bdd_reorderhandler(int a)
{
  jclass cls = (*jnienv)->FindClass(jnienv, "net/sf/javabdd/BuDDyFactory");
  jmethodID mid = (*jnienv)->GetStaticMethodID(jnienv, cls, "reorder_callback", "(I)V");
  if (mid == 0) {
    return;
  }
  (*jnienv)->CallStaticVoidMethod(jnienv, cls, mid, a);
}

/**** START OF NATIVE METHOD IMPLEMENTATIONS ****/

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    registerNatives
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_registerNatives
  (JNIEnv *env, jclass cl)
{
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    buildCube0
 * Signature: (I[I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_buildCube0
  (JNIEnv *env, jclass cl, jint value, jintArray arr)
{
  jint width, r;
  jint* a;
  jnienv = env;

  width = (*env)->GetArrayLength(env, arr);
  a = (*env)->GetIntArrayElements(env, arr, 0);
  if (a == NULL) return -1;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_buildcube(%d, %d, %p)\n", value, width, a);
#endif
  r = bdd_buildcube(value, width, (int*)a);
  (*env)->ReleaseIntArrayElements(env, arr, a, 0);
  check_error(env);
  return r;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    buildCube1
 * Signature: (I[I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_buildCube1
  (JNIEnv *env, jclass cl, jint value, jintArray arr)
{
  jint width, r;
  jint* a;
  jnienv = env;

  width = (*env)->GetArrayLength(env, arr);
  a = (*env)->GetIntArrayElements(env, arr, 0);
  if (a == NULL) return -1;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_ibuildcube(%d, %d, %p)\n", value, width, a);
#endif
  r = bdd_ibuildcube(value, width, (int*)a);
  (*env)->ReleaseIntArrayElements(env, arr, a, 0);
  check_error(env);
  return r;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    makeSet0
 * Signature: ([I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_makeSet0
  (JNIEnv *env, jclass cl, jintArray arr)
{
  jint width, r;
  jint* a;
  jnienv = env;

  width = (*env)->GetArrayLength(env, arr);
  a = (*env)->GetIntArrayElements(env, arr, 0);
  if (a == NULL) return -1;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_makeset(%p, %d)\n", a, width);
#endif
  r = bdd_makeset((int*)a, width);
  (*env)->ReleaseIntArrayElements(env, arr, a, 0);
  check_error(env);
  return r;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    initialize0
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_initialize0
  (JNIEnv *env, jobject o, jint nodesize, jint cachesize)
{
#if defined(TRACE_BUDDYLIB)
  printf("bdd_init(%d, %d)\n", nodesize, cachesize);
#endif
  bdd_init(nodesize, cachesize);
#if defined(TRACE_BUDDYLIB)
  printf("bdd_error_hook(%p)\n", bdd_errhandler);
#endif
  bdd_error_hook(bdd_errhandler);
#if defined(TRACE_BUDDYLIB)
  printf("bdd_resize_hook(%p)\n", bdd_resizehandler);
#endif
  bdd_resize_hook(bdd_resizehandler);
#if defined(TRACE_BUDDYLIB)
  printf("bdd_gbc_hook(%p)\n", bdd_gbchandler);
#endif
  bdd_gbc_hook(bdd_gbchandler);
#if defined(TRACE_BUDDYLIB)
  printf("bdd_reorder_hook(%p)\n", bdd_reorderhandler);
#endif
  bdd_reorder_hook(bdd_reorderhandler);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    isInitialized0
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_net_sf_javabdd_BuDDyFactory_isInitialized0
  (JNIEnv *env, jclass cl)
{
#if defined(TRACE_BUDDYLIB)
  printf("bdd_isrunning()\n");
#endif
  return bdd_isrunning();
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    done0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_done0
  (JNIEnv *env, jclass cl)
{
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_done()\n");
#endif
  bdd_done();
  check_error(env);
}

extern int bdderrorcond;

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    setError0
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_setError0
  (JNIEnv *env, jclass cl, jint code)
{
#if defined(TRACE_BUDDYLIB)
  printf("bdd_setError(%d)\n", code);
#endif
  bdderrorcond = code;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    clearError0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_clearError0
  (JNIEnv *env, jclass cl)
{
#if defined(TRACE_BUDDYLIB)
  printf("bdd_clearError()\n");
#endif
  bdderrorcond = 0;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    setMaxNodeNum0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_setMaxNodeNum0
  (JNIEnv *env, jclass cl, jint size)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_setmaxnodenum(%d)\n", size);
#endif
  result = bdd_setmaxnodenum(size);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    setNodeTableSize0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_setNodeTableSize0
  (JNIEnv *env, jclass cl, jint size)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_setallocnum(%d)\n", size);
#endif
  result = bdd_setallocnum(size);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    setMinFreeNodes0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_setMinFreeNodes0
  (JNIEnv *env, jclass cl, jint n)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_setminfreenodes(%d)\n", n);
#endif
  result = bdd_setminfreenodes(n);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    setMaxIncrease0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_setMaxIncrease0
  (JNIEnv *env, jclass cl, jint size)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_setmaxincrease(%d)\n", size);
#endif
  result = bdd_setmaxincrease(size);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    setIncreaseFactor0
 * Signature: (D)D
 */
JNIEXPORT jdouble JNICALL Java_net_sf_javabdd_BuDDyFactory_setIncreaseFactor0
  (JNIEnv *env, jclass cl, jdouble r)
{
  jdouble result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_setincreasefactor(%lf)\n", r);
#endif
  result = bdd_setincreasefactor(r);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    setCacheRatio0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_setCacheRatio0
  (JNIEnv *env, jclass cl, jint r)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_setcacheratio(%d)\n", r);
#endif
  result = bdd_setcacheratio(r);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    varNum0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_varNum0
  (JNIEnv *env, jclass cl)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_varnum()\n");
#endif
  result = bdd_varnum();
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    setVarNum0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_setVarNum0
  (JNIEnv *env, jclass cl, jint num)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_setvarnum(%d)\n", num);
#endif
  result = bdd_setvarnum(num);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    duplicateVar0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_duplicateVar0
  (JNIEnv *env, jclass cl, jint var)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_duplicatevar(%d)\n", var);
#endif
  result = bdd_duplicatevar(var);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    extVarNum0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_extVarNum0
  (JNIEnv *env, jclass cl, jint num)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_extvarnum(%d)\n", num);
#endif
  result = bdd_extvarnum(num);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    ithVar0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_ithVar0
  (JNIEnv *env, jclass cl, jint var)
{
  BDD b;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_ithvar(%d)\n", var);
#endif
  b = bdd_ithvar(var);
  check_error(env);
  return b;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    nithVar0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_nithVar0
  (JNIEnv *env, jclass cl, jint var)
{
  BDD b;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_nithvar(%d)\n", var);
#endif
  b = bdd_nithvar(var);
  check_error(env);
  return b;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    swapVar0
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_swapVar0
  (JNIEnv *env, jclass cl, jint v1, jint v2)
{
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_swapvar(%d, %d)\n", v1, v2);
#endif
  bdd_swapvar(v1, v2);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    makePair0
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_net_sf_javabdd_BuDDyFactory_makePair0
  (JNIEnv *env, jclass cl)
{
  bddPair* pair;
  jlong r;
  jnienv = env;

#if defined(TRACE_BUDDYLIB)
  printf("bdd_newpair()\n");
#endif
  pair = bdd_newpair();
  r = (jlong) (intptr_cast_type) pair;
  return r;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    printAll0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_printAll0
  (JNIEnv *env, jclass cl)
{
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_printall()\n");
#endif
  bdd_printall();
  fflush(stdout);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    printTable0
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_printTable0
  (JNIEnv *env, jclass cl, jint bdd)
{
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_printtable(%d)\n", bdd);
#endif
  bdd_printtable(bdd);
  fflush(stdout);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    load0
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_load0
  (JNIEnv *env, jclass cl, jstring fname)
{
  BDD r;
  int rc;
  char *str;
  jnienv = env;

  str = (char*) (*env)->GetStringUTFChars(env, fname, NULL);
  if (str == NULL) return -1;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_fnload(%s, %p)\n", str, &r);
#endif
  rc = bdd_fnload(str, &r);
  (*env)->ReleaseStringUTFChars(env, fname, str);
  check_error(env);
  return r;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    save0
 * Signature: (Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_save0
  (JNIEnv *env, jclass cl, jstring fname, jint r)
{
  int rc;
  char *str;
  jnienv = env;

  str = (char*) (*env)->GetStringUTFChars(env, fname, NULL);
  if (str == NULL) return;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_fnsave(%s, %d)\n", str, r);
#endif
  rc = bdd_fnsave(str, r);
  (*env)->ReleaseStringUTFChars(env, fname, str);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    level2Var0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_level2Var0
  (JNIEnv *env, jclass cl, jint level)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_level2var(%d)\n", level);
#endif
  result = bdd_level2var(level);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    var2Level0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_var2Level0
  (JNIEnv *env, jclass cl, jint var)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_var2level(%d)\n", var);
#endif
  result = bdd_var2level(var);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    reorder0
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_reorder0
  (JNIEnv * env, jclass cl, jint method)
{
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_reorder(%d)\n", method);
#endif
  bdd_reorder(method);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    autoReorder0
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_autoReorder0
  (JNIEnv *env, jclass cl, jint method)
{
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_autoreorder(%d)\n", method);
#endif
  bdd_autoreorder(method);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    autoReorder1
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_autoReorder1
  (JNIEnv *env, jclass cl, jint method, jint n)
{
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_autoreorder_times(%d, %d)\n", method, n);
#endif
  bdd_autoreorder_times(method, n);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    getReorderMethod0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_getReorderMethod0
  (JNIEnv *env, jclass cl)
{
  int method;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_getreorder_method()\n");
#endif
  method = bdd_getreorder_method();
  check_error(env);
  return method;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    getReorderTimes0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_getReorderTimes0
  (JNIEnv *env, jclass cl)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_getreorder_times()\n");
#endif
  result = bdd_getreorder_times();
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    disableReorder0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_disableReorder0
  (JNIEnv *env, jclass cl)
{
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_disable_reorder()\n");
#endif
  bdd_disable_reorder();
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    enableReorder0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_enableReorder0
  (JNIEnv *env, jclass cl)
{
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_enable_reorder()\n");
#endif
  bdd_enable_reorder();
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    reorderVerbose0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_reorderVerbose0
  (JNIEnv *env, jclass cl, jint level)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_reorder_verbose(%d)\n", level);
#endif
  result = bdd_reorder_verbose(level);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    setVarOrder0
 * Signature: ([I)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_setVarOrder0
  (JNIEnv *env, jclass cl, jintArray arr)
{
  jint *a;
  jint size, varnum;
  jnienv = env;
  size = (*env)->GetArrayLength(env, arr);
  varnum = bdd_varnum();
  if (size != varnum) {
    jclass cls = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    (*env)->ThrowNew(env, cls, "array size != number of vars");
    (*env)->DeleteLocalRef(env, cls);
    return;
  }
  a = (*env)->GetIntArrayElements(env, arr, 0);
  if (a == NULL) return;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_setvarorder(%p)\n", a);
#endif
  bdd_setvarorder((int*)a);
  (*env)->ReleaseIntArrayElements(env, arr, a, 0);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    addVarBlock0
 * Signature: (IZ)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_addVarBlock0
  (JNIEnv *env, jclass cl, jint var, jboolean fixed)
{
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_addvarblock(%d, %d)\n", var , fixed);
#endif
  bdd_addvarblock(var, fixed);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    addVarBlock1
 * Signature: (IIZ)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_addVarBlock1
  (JNIEnv *env, jclass cl, jint first, jint last, jboolean fixed)
{
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_intaddvarblock(%d, %d, %d)\n", first, last, fixed);
#endif
  bdd_intaddvarblock(first, last, fixed);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    varBlockAll0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_varBlockAll0
  (JNIEnv *env, jclass cl)
{
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_varblockall()\n");
#endif
  bdd_varblockall();
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    clearVarBlocks0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_clearVarBlocks0
  (JNIEnv *env, jclass cl)
{
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_clrvarblocks()\n");
#endif
  bdd_clrvarblocks();
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    printOrder0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_printOrder0
  (JNIEnv *env, jclass cl)
{
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_printorder()\n");
#endif
  bdd_printorder();
  fflush(stdout);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    nodeCount0
 * Signature: ([I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_nodeCount0
  (JNIEnv *env, jclass cl, jintArray arr)
{
  jint *a;
  jint size;
  int result;
  jnienv = env;
  size = (*env)->GetArrayLength(env, arr);
  a = (*env)->GetIntArrayElements(env, arr, 0);
  if (a == NULL) return -1;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_anodecount(%p, %d)\n", a, size);
#endif
  result = bdd_anodecount((int*)a, size);
  (*env)->ReleaseIntArrayElements(env, arr, a, 0);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    getAllocNum0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_getAllocNum0
  (JNIEnv *env, jclass c)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_getallocnum()\n");
#endif
  result = bdd_getallocnum();
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    getCacheSize0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_getCacheSize0
  (JNIEnv *env, jclass c)
{
  int result;
  bddStat stats;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_stats(%p)\n", &stats);
#endif
  bdd_stats(&stats);
  result = stats.cachesize;
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    getNodeNum0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_getNodeNum0
  (JNIEnv *env, jclass c)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_getnodenum()\n");
#endif
  result = bdd_getnodenum();
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    reorderGain0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_reorderGain0
  (JNIEnv *env, jclass c)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_reorder_gain()\n");
#endif
  result = bdd_reorder_gain();
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    printStat0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_printStat0
  (JNIEnv *env, jclass c)
{
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_printstat()\n");
#endif
  bdd_printstat();
  fflush(stdout);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory
 * Method:    getVersion0
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_net_sf_javabdd_BuDDyFactory_getVersion0
  (JNIEnv *env, jclass c)
{
  char *buf;
  jstring result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_versionstr()\n");
#endif
  buf = bdd_versionstr();
  result = (*env)->NewStringUTF(env, buf);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    var0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_var0
  (JNIEnv *env, jclass cl, jint b)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_var(%d)\n", b);
#endif
  result = bdd_var(b);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    high0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_high0
  (JNIEnv *env, jclass cl, jint b)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_high(%d)\n", b);
#endif
  result = bdd_high(b);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    low0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_low0
  (JNIEnv *env, jclass cl, jint b)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_low(%d)\n", b);
#endif
  result = bdd_low(b);
  check_error(env);
  return result;}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    not0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_not0
  (JNIEnv *env, jclass cl, jint b)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_not(%d)\n", b);
#endif
  result = bdd_not(b);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    ite0
 * Signature: (III)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_ite0
  (JNIEnv *env, jclass cl, jint b, jint c, jint d)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_ite(%d, %d, %d)\n", b, c, d);
#endif
  result = bdd_ite(b, c, d);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    relprod0
 * Signature: (III)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_relprod0
  (JNIEnv *env, jclass cl, jint b, jint c, jint d)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_relprod(%d, %d, %d)\n", b, c, d);
#endif
  result = bdd_relprod(b, c, d);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    compose0
 * Signature: (III)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_compose0
  (JNIEnv *env, jclass cl, jint b, jint c, jint v)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_compose(%d, %d, %d)\n", b, c, v);
#endif
  result = bdd_compose(b, c, v);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    constrain0
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_constrain0
  (JNIEnv *env, jclass cl, jint b, jint c)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_constrain(%d, %d)\n", b, c);
#endif
  result = bdd_constrain(b, c);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    exist0
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_exist0
  (JNIEnv *env, jclass cl, jint b, jint c)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_exist(%d, %d)\n", b, c);
#endif
  result = bdd_exist(b, c);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    forAll0
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_forAll0
  (JNIEnv *env, jclass cl, jint b, jint c)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_forall(%d, %d)\n", b, c);
#endif
  result = bdd_forall(b, c);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    unique0
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_unique0
  (JNIEnv *env, jclass cl, jint b, jint c)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_unique(%d, %d)\n", b, c);
#endif
  result = bdd_unique(b, c);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    restrict0
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_restrict0
  (JNIEnv *env, jclass cl, jint b, jint c)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_restrict(%d, %d)\n", b, c);
#endif
  result = bdd_restrict(b, c);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    simplify0
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_simplify0
  (JNIEnv *env, jclass cl, jint b, jint c)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_simplify(%d, %d)\n", b, c);
#endif
  result = bdd_simplify(b, c);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    support0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_support0
  (JNIEnv *env, jclass cl, jint b)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_support(%d)\n", b);
#endif
  result = bdd_support(b);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    apply0
 * Signature: (III)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_apply0
  (JNIEnv *env, jclass cl, jint b, jint c, jint operation)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_apply(%d, %d, %d)\n", b, c, operation);
#endif
  result = bdd_apply(b, c, operation);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    applyAll0
 * Signature: (IIII)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_applyAll0
  (JNIEnv *env, jclass cl, jint b, jint c, jint operation, jint d)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_appall(%d, %d, %d, %d)\n", b, c, operation, d);
#endif
  result = bdd_appall(b, c, operation, d);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    applyEx0
 * Signature: (IIII)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_applyEx0
  (JNIEnv *env, jclass cl, jint b, jint c, jint operation, jint d)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_appex(%d, %d, %d, %d)\n", b, c, operation, d);
#endif
  result = bdd_appex(b, c, operation, d);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    applyUni0
 * Signature: (IIII)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_applyUni0
  (JNIEnv *env, jclass cl, jint b, jint c, jint operation, jint d)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_appuni(%d, %d, %d, %d)\n", b, c, operation, d);
#endif
  result = bdd_appuni(b, c, operation, d);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    satOne0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_satOne0
  (JNIEnv *env, jclass cl, jint b)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_satone(%d)\n", b);
#endif
  result = bdd_satone(b);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    fullSatOne0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_fullSatOne0
  (JNIEnv *env, jclass cl, jint b)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_fullsatone(%d)\n", b);
#endif
  result = bdd_fullsatone(b);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    satOne1
 * Signature: (III)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_satOne1
  (JNIEnv *env, jclass cl, jint b, jint c, jint d)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_satoneset(%d, %d, %d)\n", b, c, d);
#endif
  result = bdd_satoneset(b, c, d);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    printSet0
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_printSet0
  (JNIEnv *env, jclass cl, jint b)
{
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_printset(%d)\n", b);
#endif
  bdd_printset(b);
  fflush(stdout);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    printDot0
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_printDot0
  (JNIEnv *env, jclass cl, jint b)
{
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_printdot(%d)\n", b);
#endif
  bdd_printdot(b);
  fflush(stdout);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    nodeCount0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_nodeCount0
  (JNIEnv *env, jclass cl, jint b)
{
  int result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_nodecount(%d)\n", b);
#endif
  result = bdd_nodecount(b);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    pathCount0
 * Signature: (I)D
 */
JNIEXPORT jdouble JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_pathCount0
  (JNIEnv *env, jclass cl, jint b)
{
  double result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_pathcount(%d)\n", b);
#endif
  result = bdd_pathcount(b);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    satCount0
 * Signature: (I)D
 */
JNIEXPORT jdouble JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_satCount0
  (JNIEnv *env, jclass cl, jint b)
{
  double result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_satcount(%d)\n", b);
#endif
  result = bdd_satcount(b);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    satCount1
 * Signature: (II)D
 */
JNIEXPORT jdouble JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_satCount1
  (JNIEnv *env, jclass cl, jint b, jint c)
{
  double result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_satcountset(%d, %d)\n", b, c);
#endif
  result = bdd_satcountset(b, c);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    logSatCount0
 * Signature: (I)D
 */
JNIEXPORT jdouble JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_logSatCount0
  (JNIEnv *env, jclass cl, jint b)
{
  double result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_satcountln(%d)\n", b);
#endif
  result = bdd_satcountln(b);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    logSatCount1
 * Signature: (II)D
 */
JNIEXPORT jdouble JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_logSatCount1
  (JNIEnv *env, jclass cl, jint b, jint c)
{
  double result;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_satcountlnset(%d, %d)\n", b, c);
#endif
  result = bdd_satcountlnset(b, c);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    varProfile0
 * Signature: (I)[I
 */
JNIEXPORT jintArray JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_varProfile0
  (JNIEnv *env, jclass cl, jint b)
{
  jintArray result;
  int size;
  int* arr;
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_varnum()\n");
#endif
  size = bdd_varnum();
#if defined(TRACE_BUDDYLIB)
  printf("bdd_varprofile(%d)\n", b);
#endif
  arr = bdd_varprofile(b);
  if (check_error(env)) return NULL;
  if (arr == NULL) return NULL;
  result = (*env)->NewIntArray(env, size);
  if (result == NULL) return NULL;
  (*env)->SetIntArrayRegion(env, result, 0, size, (jint*) arr);
  free(arr);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    addRef
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_addRef
  (JNIEnv *env, jclass cl, jint b)
{
  jnienv = env;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_addref(%d)\n", b);
#endif
  bdd_addref(b);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    delRef
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_delRef
  (JNIEnv *env, jclass cl, jint b)
{
  jnienv = env;
  if (b != INVALID_BDD) {
#if defined(TRACE_BUDDYLIB)
    printf("bdd_delref(%d)\n", b);
#endif
    bdd_delref(b);
    check_error(env);
  }
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    veccompose0
 * Signature: (IJ)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_veccompose0
  (JNIEnv *env, jclass cl, jint b, jlong pair)
{
  int result;
  bddPair* p;
  jnienv = env;
  p = (bddPair*) (intptr_cast_type) pair;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_veccompose(%d, %p)\n", b, p);
#endif
  result = bdd_veccompose(b, p);
  check_error(env);
  return result;
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDD
 * Method:    replace0
 * Signature: (IJ)I
 */
JNIEXPORT jint JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDD_replace0
  (JNIEnv *env, jclass cl, jint b, jlong pair)
{
  int result;
  bddPair* p;
  jnienv = env;
  p = (bddPair*) (intptr_cast_type) pair;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_replace(%d, %p)\n", b, p);
#endif
  result = bdd_replace(b, p);
  check_error(env);
  return result;
}

/* class net_sf_javabdd_BuDDyFactory_BuDDyBDDPairing */

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDDPairing
 * Method:    set0
 * Signature: (JII)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDDPairing_set0
  (JNIEnv *env, jclass cl, jlong pair, jint i, jint j)
{
  bddPair* p;
  jnienv = env;
  p = (bddPair*) (intptr_cast_type) pair;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_setpair(%p, %d, %d)\n", p, i, j);
#endif
  bdd_setpair(p, i, j);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDDPairing
 * Method:    set1
 * Signature: (J[I[I)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDDPairing_set1
  (JNIEnv *env, jclass cl, jlong pair, jintArray arr1, jintArray arr2)
{
  jint size1, size2;
  jint *a1;
  jint *a2;
  bddPair* p;
  jnienv = env;
  p = (bddPair*) (intptr_cast_type) pair;
  size1 = (*env)->GetArrayLength(env, arr1);
  size2 = (*env)->GetArrayLength(env, arr2);
  if (size1 != size2) {
    jclass cls = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    (*env)->ThrowNew(env, cls, "array sizes do not match");
    (*env)->DeleteLocalRef(env, cls);
    return;
  }
  a1 = (*env)->GetPrimitiveArrayCritical(env, arr1, NULL);
  if (a1 != NULL) {
    a2 = (*env)->GetPrimitiveArrayCritical(env, arr2, NULL);
    if (a2 != NULL) {
#if defined(TRACE_BUDDYLIB)
      printf("bdd_setpairs(%p, %p, %p, %d)\n", p, a1, a2, size1);
#endif
      bdd_setpairs(p, (int*)a1, (int*)a2, size1);
      (*env)->ReleasePrimitiveArrayCritical(env, arr2, a2, JNI_ABORT);
    }
    (*env)->ReleasePrimitiveArrayCritical(env, arr1, a1, JNI_ABORT);
  }
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDDPairing
 * Method:    set2
 * Signature: (JII)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDDPairing_set2
  (JNIEnv *env, jclass cl, jlong pair, jint b, jint c)
{
  bddPair* p;
  jnienv = env;
  p = (bddPair*) (intptr_cast_type) pair;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_setbddpair(%p, %d, %d)\n", p, b, c);
#endif
  bdd_setbddpair(p, b, c);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDDPairing
 * Method:    set3
 * Signature: (J[I[I)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDDPairing_set3
  (JNIEnv *env, jclass cl, jlong pair, jintArray arr1, jintArray arr2)
{
  jint size1, size2;
  bdd *a1;
  bdd *a2;
  bddPair* p;
  jnienv = env;
  p = (bddPair*) (intptr_cast_type) pair;
  size1 = (*env)->GetArrayLength(env, arr1);
  size2 = (*env)->GetArrayLength(env, arr2);
  if (size1 != size2) {
    jclass cls = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    (*env)->ThrowNew(env, cls, "array sizes do not match");
    (*env)->DeleteLocalRef(env, cls);
    return;
  }
  a1 = (*env)->GetPrimitiveArrayCritical(env, arr1, NULL);
  if (a1 != NULL) {
    a2 = (*env)->GetPrimitiveArrayCritical(env, arr2, NULL);
    if (a2 != NULL) {
#if defined(TRACE_BUDDYLIB)
      printf("bdd_setbddpairs(%p, %p, %p, %d)\n", p, a1, a2, size1);
#endif
      bdd_setbddpairs(p, (int*)a1, (int*)a2, size1);
      (*env)->ReleasePrimitiveArrayCritical(env, arr2, a2, JNI_ABORT);
    }
    (*env)->ReleasePrimitiveArrayCritical(env, arr1, a1, JNI_ABORT);
  }
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDDPairing
 * Method:    reset0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDDPairing_reset0
  (JNIEnv *env, jclass cl, jlong pair)
{
  bddPair* p;
  jnienv = env;
  p = (bddPair*) (intptr_cast_type) pair;
#if defined(TRACE_BUDDYLIB)
  printf("bdd_resetpair(%p)\n", p);
#endif
  bdd_resetpair(p);
  check_error(env);
}

/*
 * Class:     net_sf_javabdd_BuDDyFactory_BuDDyBDDPairing
 * Method:    free0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_net_sf_javabdd_BuDDyFactory_00024BuDDyBDDPairing_free0
  (JNIEnv *env, jclass cl, jlong pair)
{
  bddPair* p;
  jnienv = env;
  p = (bddPair*) (intptr_cast_type) pair;
  if (p) {
#if defined(TRACE_BUDDYLIB)
    printf("bdd_freepair(%p)\n", p);
#endif
    bdd_freepair(p);
  }
}


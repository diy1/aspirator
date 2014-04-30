#include <stdlib.h>
#include <assert.h>

#ifdef GENERATE_TRACE

/* Windows peculiarities */
#if defined(_MSC_VER) || defined(WIN32)
#define __inline__
#define __func__ __FUNCTION__
#else
#define HAS_UNISTD_H
#endif

#define BUDDY_PROLOGUE \
	int was_enabled = trace_enable; \
	if(was_enabled) \
		trace_begin_function(__func__); \
	trace_enable=0

#define BUDDY_IGNOREFN_PROLOGUE int was_enabled = trace_enable;trace_enable = 0
#define BUDDY_IGNOREFN_EPILOGUE trace_enable = was_enabled

#define ADD_ARG1(type,expr) if(was_enabled) trace_add_arg(type,(void *)expr, NULL)
#define ADD_ARG2(type,expr1,expr2)  if(was_enabled) trace_add_arg(type,(void *)expr1, (void *)expr2)


#define RETURN_BDD(expr) \
	if(was_enabled) \
	{ \
		trace_enable = 1; \
		return (BDD) trace_end_function_bdd(T_BDD,(void *)expr); \
	} else return expr; 

#define RETURN_BDD_PAIR(expr) \
	if(was_enabled) \
	{ \
		trace_enable = 1; \
		return (bddPair *) trace_end_function_bdd(T_BDD_PAIR,(void *)expr); \
	} else return expr; 

#define RETURN(expr) \
	do \
	{ \
		if(was_enabled) \
		{\
			trace_enable = 1; \
			trace_end_function(); \
		} \
		return expr; \
	} while(0) 


enum arg_type
{
	T_INT,
	T_INT_PTR,
	T_CHAR_PTR,
	T_BDD,
	T_BDD_PTR,
	T_BDD_LOAD,
	T_BDD_PAIR
};

void trace_init(const char *filename);
void trace_add_bdd(enum arg_type type,void * val);
void trace_del_bdd(enum arg_type type, void * val);
void trace_begin_function(const char * fn);
void trace_end_function(void);
void * trace_end_function_bdd(enum arg_type type, void * arg);

void trace_add_arg(enum arg_type type, void * arg1, void * arg2);

void output_trace(void);

extern int trace_enable;
extern int trace_outputted;

#else // GENERATE_TRACE

#define BUDDY_PROLOGUE do { } while (0);
#define BUDDY_IGNOREFN_PROLOGUE do { } while (0);
#define BUDDY_IGNOREFN_EPILOGUE do { } while (0);
#define ADD_ARG1(type,expr) do { } while (0);
#define ADD_ARG2(type,expr1,expr2) do { } while (0);
#define RETURN_BDD(expr) do { return expr; } while (0);
#define RETURN_BDD_PAIR(expr) do { return expr; } while (0);
#define RETURN(expr) do { return expr; } while (0);
#define trace_init(filename) do { } while (0);
#define trace_del_bdd(type,val) do { } while (0);
#define output_trace() do { } while (0);

#endif // !GENERATE_TRACE

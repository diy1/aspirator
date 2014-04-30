
#ifdef GENERATE_TRACE

#if defined(HAS_UNISTD_H)
#include <unistd.h>
#endif
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <string.h>

#include "bdd.h"

#ifndef EXIT_FAILURE
#define EXIT_FAILURE -1
#endif

/* Simple trace file generation */


#define FAST_MALLOC

#ifdef FAST_MALLOC
static char* bufferStart = 0;
static char* bufferPtr = 0;
static char* bufferEnd = 0;
static int startSize = 1<<16;
static void grow_malloc(int sz)
{
	int oldSize, newSize;
	oldSize = bufferEnd - bufferStart;
	newSize = oldSize*2;
	if (newSize < oldSize+sz) newSize = oldSize+sz;
	if (newSize < startSize) newSize = startSize;
	bufferStart = malloc(newSize);
	assert(bufferStart);
	bufferPtr = bufferStart;
	bufferEnd = bufferStart + newSize;
}

static __inline__ void * xmalloc(int sz)
{
	void * retval;
	if (bufferEnd - bufferPtr < sz) {
		grow_malloc(sz);
	}
	retval = bufferPtr;
	bufferPtr += sz;
	return retval;
}
#else
static __inline__ void * xmalloc(int sz)
{
	void * retval = malloc(sz);
	assert(retval);
	return retval;
}
#endif



FILE * tracefp;
const char * trace_fname;
#define NR_TABLE (1<<16)
#define HASH_MASK ((NR_TABLE)-1)

struct bdd_decl
{
	struct bdd_decl * next;
	union
	{
		BDD bval;
		bddPair  * pval;
	};

	int is_pair, is_collected;
	const char * identifier;
};

struct bdd_decl * decltbl[NR_TABLE];

struct bdd_arg
{
	struct bdd_arg * next;
	enum arg_type type;
	
	union
	{
		const char * ident; /* T_BDD, T_BDD_PAIR */

		struct /* T_BDD_PTR */
		{
			BDD * barr;
			int blen;
		};

		struct /* T_INT_PTR */
		{
			int * iarr;
			int len;
		}; 

		char * str; /* T_CHAR_PTR */

		int i; /* T_INT */
	};
};

struct bdd_function
{
	struct bdd_function * next, * prev;
	const char * fn_ident;
	struct bdd_arg * args; /* NULL if void */
	const char * retval;
};


struct bdd_function * currfn;
struct bdd_function * bddfns;

const char * last_id;

static unsigned long hash_bdd(BDD b)
{
	return (unsigned long)b & HASH_MASK;
}

static unsigned long hash_bddpair(bddPair * p)
{
	unsigned long tmp = (unsigned long)p;
	return tmp & HASH_MASK;
}

static __inline__ int id_done(const char * id)
{
	const char * pos = id + 4; /* skip _bdd header */

	while(*pos == 'z') pos++;

	if(*pos == '\0') return 1;
	return 0;
}

static const char * genid(void)
{
	char * retval; 
	int i,j;

	if(!last_id)
	{
		retval = "_bdda";
	} else if(id_done(last_id))
	{
		retval = xmalloc(strlen(last_id) + 2);
		memcpy(retval,"_bdd",4);
		memset(retval+4,'a',strlen(last_id)-4 + 1);
		retval[strlen(last_id)+1] = '\0';
	} else
	{
		for(i = 4; last_id[i] == 'z' ; i++) ;
		retval = strdup(last_id);

		for(j = 4; j < i; j++)
			retval[j] = 'a';
		retval[i]++;
	}

	last_id = retval;
	return retval;
}

static struct bdd_decl * lookup_bdd(enum arg_type type, void * arg)
{
	struct bdd_decl * d;


	if(type == T_BDD)
	{
		BDD b = (BDD) arg;
		d = decltbl[hash_bdd(b)];


		while(d)
		{
			if(!(d->is_pair) && d->bval == b && !d->is_collected)
				return d;
			d = d->next;
		}
	} else if(type == T_BDD_PAIR)
	{
		bddPair * p = (bddPair *) arg;
		d = decltbl[hash_bddpair(p)];
		while(d)
		{
			if( d->is_pair && d->pval == p & !d->is_collected)
			{
				return d;
			}
			d = d->next;
		}
	} else assert(0);

	return NULL;
}

static const char * lookup_bdd_ident(enum arg_type type, void * arg)
{
	struct bdd_decl * d;
	if(type == T_BDD)
	{
		BDD b = (BDD) arg;
		if(b == bddtrue) return "bddtrue";
		if(b == bddfalse) return "bddfalse";
	}
	d = lookup_bdd(type,arg);
	if(!d) return NULL; 

	return d->identifier;
}


void trace_init(const char * filename)
{
	tracefp = fopen(filename,"w");
	if(!tracefp)
	{
		fprintf(stderr,"Unable to open file %s for tracing\n",filename);
		exit(EXIT_FAILURE);
	}
	atexit(output_trace);
        memset(decltbl, 0, sizeof(decltbl));
        currfn = NULL;
        bddfns = NULL;
}

void trace_begin_function(const char * fn)
{
	assert(!currfn);
	currfn = xmalloc(sizeof(struct bdd_function));
	currfn->fn_ident = fn;
	currfn->retval = NULL;
	currfn->args = NULL;
	currfn->next =  currfn->prev = currfn;
}

void trace_end_function(void)
{

	if(bddfns == NULL) bddfns = currfn;
	else
	{
		bddfns->prev->next = currfn;
		currfn->next = bddfns;
		currfn->prev = bddfns->prev;
		bddfns->prev = currfn;

	}
	currfn = NULL;
}

void trace_add_bdd(enum arg_type type, void * retval)
{
	if(!lookup_bdd_ident(type,retval))
	{
		struct bdd_decl * d = xmalloc(sizeof(struct bdd_decl));
		d->identifier = genid();
		d->is_pair = type == T_BDD_PAIR;
		d->is_collected = 0;
		if(type == T_BDD)
		{
			BDD b = (BDD) retval;
			d->bval = b;
			d->next = decltbl[hash_bdd(b)];
			decltbl[hash_bdd(b)] = d;
		}
		else
		{
			bddPair * p = (bddPair *) retval;
			if(!p) 
			{
				printf("ERROR at %s\n",__func__);
				return;
			}
			d->pval = p;
			d->next = decltbl[hash_bddpair(p)];
			decltbl[hash_bddpair(p)] = d;
		}

	}
}

void trace_del_bdd(enum arg_type type, void * arg)
{
	struct bdd_decl * d = lookup_bdd(type,arg);
	if(d)
	{
		if(type == T_BDD_PAIR)
			printf("removing pair\n");
		d->is_collected = 1;
	}
}

void * trace_end_function_bdd(enum arg_type type, void * retval)
{
	const char * id;

	trace_add_bdd(type,retval);

	id = lookup_bdd_ident(type,retval);
	assert(id);

	currfn->retval = id;
	trace_end_function();
	return retval;
}

void trace_add_arg(enum arg_type type, void * arg1, void * arg2)
{
	struct bdd_arg *a = xmalloc(sizeof(struct bdd_arg));
	a->next = NULL;
	a->type = type;
	switch(type)
	{
		case T_INT:
			a->i = (int) arg1;
			break;
		case T_INT_PTR:
			a->len = (int) arg2;
			a->iarr = xmalloc(a->len * sizeof(int));
			memcpy(a->iarr,arg1,a->len * sizeof(int));
			break;
		case T_CHAR_PTR:
			a->str = strdup((char *)arg1);
			break;
		case T_BDD:
		case T_BDD_PAIR:
			a->ident = lookup_bdd_ident(type,arg1);
			assert(a->ident);
			break;
		case T_BDD_LOAD:
			{
				BDD b = (BDD) arg1;
				trace_add_bdd(T_BDD,(void *)b);
				a->ident = lookup_bdd_ident(T_BDD,(void *)b);
				assert(a->ident);
				break;
			}
		case T_BDD_PTR:
			{
				int i;
				a->blen = (int) arg2;
				a->barr = malloc(sizeof(BDD) * a->blen);
				memcpy(a->barr,arg1,sizeof(BDD) * a->blen);

				/* broken code manufactures bdds and then
				 * passes them as aray elements. bleh
				 */
				for(i = 0; i < a->blen; i++)
				{
					trace_add_bdd(T_BDD,(void *)a->barr[i]);
				}
				break;
			}

		default:
			assert(0);
	}

	if(currfn->args == NULL) currfn->args = a;
	else
	{
		struct bdd_arg * c =  currfn->args;
		while(c->next) c = c->next;
		c->next = a;
	}

}

static void print_args(struct bdd_arg * args)
{
	struct bdd_arg * a = args;

	while(a)
	{
		switch(a->type)
		{
			case T_INT:
				fprintf(tracefp,"%d",a->i);
				break;
			case T_INT_PTR:
				{
					int i;
					fprintf(tracefp,"(int []) { ");
					for(i = 0; i < a->len; i++)
						fprintf(tracefp,"%d,",
								a->iarr[i]);
					fprintf(tracefp,"}");
					break;
				}
			case T_CHAR_PTR:
				fprintf(tracefp,"\"%s\"",a->str);
				break;
			case T_BDD:
			case T_BDD_PAIR:
				fprintf(tracefp,"%s",a->ident);
				break;
			case T_BDD_LOAD:
				fprintf(tracefp,"&%s",a->ident);
				break;
			case T_BDD_PTR:
			{
				int i;
				fprintf(tracefp, "(BDD []) {");
				for(i = 0; i < a->blen; i++)
				{
					fprintf(tracefp,"%s,",
						lookup_bdd_ident(T_BDD,(void *)a->barr[i]));
				}
				fprintf(tracefp,"}");

				break;
			}

			default: assert(0);
		}
		if(a->next)
			fprintf(tracefp,",");
		a = a->next;
	}
}

void emit_header(void)
{
	fprintf(tracefp,"#include \"bdd.h\"\n");
	fprintf(tracefp,"int main(void)\n{\n");
}

void emit_trailer(void)
{
	fprintf(tracefp,"return 0; }\n");

}
void output_trace(void)
{
	int i, first;
	struct bdd_function * f;

	if(trace_outputted) return;

	trace_outputted = 1;

	first = 0;

	emit_header();

	for(i = 0; i < NR_TABLE; i++)
	{
		struct bdd_decl * d = decltbl[i];

		while(d)
		{
			if(d->is_pair)
				fprintf(tracefp,"\tbddPair *");
			else
				fprintf(tracefp,"\tBDD ");
			fprintf(tracefp,"%s;\n",d->identifier);
			d = d->next;
		}
	}

	f = bddfns;

	while(f != bddfns || !first)
	{
		if(f->retval && strcmp(f->retval,"bddtrue") 
				&& strcmp(f->retval,"bddfalse"))
			fprintf(tracefp,"\n\t%s = ", f->retval);
		else
			fprintf(tracefp,"\n\t");
		fprintf(tracefp, f->fn_ident);
		fprintf(tracefp,"(");
		print_args(f->args);
		fprintf(tracefp,");");
		f = f->next;
		first = 1;
	}

	emit_trailer();
}

#endif // GENERATE_TRACE

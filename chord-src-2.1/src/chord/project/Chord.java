package chord.project;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation on a class defining a Java task.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface Chord {
    String name() default "";    // may be empty
    String sign() default "";    // if non-empty then annotated class must be a subclass of ProgramRel
                                // format e.g.: "M0,F0,V0:M0_V0xF0" or just "M0,F0,V0"
    String prescriber() default "";
    String[] consumes() default { };
    String[] produces() default { };
    String[] controls() default { };
    String[] namesOfTypes() default { };
    Class [] types() default { };
    String[] namesOfSigns() default { };
    String[] signs() default { };
};

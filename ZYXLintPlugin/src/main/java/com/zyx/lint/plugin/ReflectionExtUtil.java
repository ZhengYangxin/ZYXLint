package com.zyx.lint.plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * desc :
 * time  : 16/12/18.
 * author : pielan
 */

public class ReflectionExtUtil {


    /**
     * 新建实例,要么不写构造函数，否则必须有一个默认构造函数
     *
     * @param className
     *            类名
     * @param args
     *            构造函数的参数
     * @return 新建的实例
     * @throws Exception
     *             Exception
     */
    public static Object newInstance(String className, Object[] args)
            throws Exception {
        Class newoneClass = Class.forName(className);
        if (args == null) {
            return newoneClass.newInstance();
        }
        Class[] argsClass = new Class[args.length];

        for (int i = 0, j = args.length; i < j; i++) {
            argsClass[i] = args[i].getClass();
        }

        Constructor cons = newoneClass.getConstructor(argsClass);

        return cons.newInstance(args);

    }

    /**
     * 是不是某个类的实例
     *
     * @param obj
     *            实例
     * @param cls
     *            类
     * @return 如果 obj 是此类的实例，则返回 true
     */
    public static boolean isInstance(Object obj, Class cls) {
        return cls.isInstance(obj);
    }

    /**
     * 循环向上转型, 获取对象的 DeclaredMethod
     *
     * @param object
     *            : 子类对象
     * @param methodName
     *            : 父类中的方法名
     * @param parameterTypes
     *            : 父类中的方法参数类型
     * @return 父类中的方法对象
     */

    public static Method getDeclaredMethod(Object object, String methodName,
                                           Class<?>... parameterTypes) {
        Method method = null;

        for (Class<?> clazz = object.getClass(); clazz != Object.class; clazz = clazz
                .getSuperclass()) {
            try {
                method = clazz.getDeclaredMethod(methodName, parameterTypes);
                return method;
            } catch (Exception e) {
                // 这里甚么都不要做！并且这里的异常必须这样写，不能抛出去。
                // 如果这里的异常打印或者往外抛，则就不会执行clazz =
                // clazz.getSuperclass(),最后就不会进入到父类中了

            }
        }

        return null;
    }

    /**
     * 直接调用对象方法, 而忽略修饰符(private, protected, default)
     *
     * @param object
     *            : 子类对象
     * @param methodName
     *            : 父类中的方法名
     * @param parameterTypes
     *            : 父类中的方法参数类型
     * @param parameters
     *            : 父类中的方法参数
     * @return 父类中方法的执行结果
     */

    public static Object invokeMethod(Object object, String methodName,
                                      Class<?>[] parameterTypes, Object[] parameters) {
        // 根据 对象、方法名和对应的方法参数 通过反射 调用上面的方法获取 Method 对象
        Method method = getDeclaredMethod(object, methodName, parameterTypes);

        // 抑制Java对方法进行检查,主要是针对私有方法而言
        method.setAccessible(true);

        try {
            if (null != method) {

                // 调用object 的 method 所代表的方法，其方法的参数是 parameters
                return method.invoke(object, parameters);
            }
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        }

        return null;
    }

    private static Field[] arryConcat(Field[] a, Field[] b) {
        Field[] c = new Field[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    private static Method[] arryConcat(Method[] a, Method[] b) {
        Method[] c = new Method[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    /**
     * 获取对象的所有属性，包括父类
     *
     * @param object
     *            Object
     * @return Field []
     */
    public static Field[] getDeclaredFields(Object object) {
        Class<?> clazz = object.getClass();
        Field[] total = new Field[0];
        for (; clazz != Object.class; clazz = clazz.getSuperclass()) {
            total = (Field[]) arryConcat(total, clazz.getDeclaredFields());
        }
        return total;
    }

    /**
     * 获取对象的所有属性，包括父类
     *
     * @param clazz
     *            Class<?>
     * @return Field []
     */
    public static Field[] getDeclaredFields(Class<?> clazz) {
        Field[] total = new Field[0];
        for (; clazz != Object.class; clazz = clazz.getSuperclass()) {
            total = (Field[]) arryConcat(total, clazz.getDeclaredFields());
        }
        return total;
    }

    /**
     * 获取对象的所有方法
     *
     * @param object
     *            Object
     * @return Method []
     */
    public static Method[] getDeclaredMethods(Object object) {
        Class<?> clazz = object.getClass();
        Method[] total = new Method[0];
        for (; clazz != Object.class; clazz = clazz.getSuperclass()) {
            total = (Method[]) arryConcat(total, clazz.getDeclaredMethods());
        }
        return total;
    }

    /**
     * 循环向上转型, 获取对象的 DeclaredField
     *
     * @param object
     *            : 子类对象
     * @param fieldName
     *            : 父类中的属性名
     * @return 父类中的属性对象
     */

    public static Field getDeclaredField(Object object, String fieldName) {
        Field field = null;

        Class<?> clazz = object.getClass();

        for (; clazz != Object.class; clazz = clazz.getSuperclass()) {
            try {
                field = clazz.getDeclaredField(fieldName);
                return field;
            } catch (Exception e) {
            }
        }

        return null;
    }

    /**
     * 直接设置对象属性值, 忽略 private/protected 修饰符, 也不经过 setter
     *
     * @param object
     *            : 子类对象
     * @param fieldName
     *            : 父类中的属性名
     * @param value
     *            : 将要设置的值
     */

    public static void setFieldValue(Object object, String fieldName,
                                     Object value) {

        // 根据 对象和属性名通过反射 调用上面的方法获取 Field对象
        Field field = getDeclaredField(object, fieldName);

        // 抑制Java对其的检查
        field.setAccessible(true);

        try {
            // 将 object 中 field 所代表的值 设置为 value
            field.set(object, value);
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        }

    }

    /**
     * 直接读取对象的属性值, 忽略 private/protected 修饰符, 也不经过 getter
     *
     * @param object
     *            : 子类对象
     * @param fieldName
     *            : 父类中的属性名
     * @return : 父类中的属性值
     */

    public static Object getFieldValue(Object object, String fieldName) {

        // 根据 对象和属性名通过反射 调用上面的方法获取 Field对象
        Field field = getDeclaredField(object, fieldName);

        // 抑制Java对其的检查
        field.setAccessible(true);

        try {
            // 获取 object 中 field 所代表的属性值
            return field.get(object);

        } catch (Exception e) {
        }

        return null;
    }

    /**
     * 设置属性值
     *
     * @param list
     *            ArrayList
     * @param cla
     *            Class
     * @return ArrayList
     */
    @SuppressWarnings("unchecked")
    public static ArrayList array2bean(ArrayList list, Class cla) {
        ArrayList result = new ArrayList();
        int filedLen = cla.getDeclaredFields().length;
        System.out.println(":" + cla.getDeclaredFields().length);
        for (int i = 0; i < list.size(); i++) {
            Object[] o = (Object[]) list.get(i);
            int length = filedLen < o.length ? filedLen : o.length;
            try {
                result.add(cla.newInstance());
                for (int j = 0; j < length; j++) {
                    Method m = null;
                    String mName = cla.getDeclaredFields()[j].getName();
                    mName = mName.substring(0, 1).toUpperCase()
                            + mName.substring(1);
                    mName = "set" + mName;
                    m = cla.getMethod(mName, new Class[] { String.class });
                    // 调用设置的方法，给属性赋值
                    m.invoke(result.get(i), new Object[] { o[j] == null ? ""
                            : o[j].toString() });
                }
            } catch (Exception e) {
            }
        }
        return result;
    }

    /**
     *
     * @param cla
     *            String
     * @param attri
     *            Class
     * @return Class
     */
    public static Class getAttriTypeByName(Class cla, String attri) {
        Field[] fds = getDeclaredFields(cla);
        for (Field field : fds) {
            if (field.getName().equals(attri)) {
                return field.getType();
            }
        }
        return String.class;
    }


    /**
     * 根据MAP 设置对象的所有属性，
     *
     * @param obj
     *            Object
     * @param valueMap
     *            Map<String, Object>
     * @throws NoSuchMethodException
     *             NoSuchMethodException
     * @throws SecurityException
     *             SecurityException
     * @throws InvocationTargetException
     *             InvocationTargetException
     * @throws IllegalAccessException
     *             IllegalAccessException
     * @throws IllegalArgumentException
     *             IllegalArgumentException
     */
    public static void setAttrsValueMap(Object obj, Map<String, Object> valueMap)
            throws SecurityException, NoSuchMethodException,
            IllegalArgumentException, IllegalAccessException,
            InvocationTargetException {
        Class cla = obj.getClass();

        String fieldName = null;
        String methodName = null;
        Method method = null;
        for (Map.Entry<String, Object> entry : valueMap.entrySet()) {
            // 预判断
            if (entry.getValue() == null
                    || String.valueOf(entry.getValue()).startsWith("noSuch")) {
                continue;
            }
            fieldName = entry.getKey();
            if (fieldName.equals("staticChannel")) {
                System.out.println("xxx");
            }
            methodName = "set" + fieldName.substring(0, 1).toUpperCase()
                    + fieldName.substring(1);
            Class type = entry.getValue().getClass();
            if (type == Integer.class) {
                type = int.class;
            } else if (type == Boolean.class) {
                type = boolean.class;
            }
            // Object varObj = convertClxValue(type, entry.getValue());
            method = getDeclaredMethod(obj, methodName, type);
            // if(method == null){
            // type = int.class;
            // method = getDeclaredMethod(obj,methodName, type);
            // }
            if (method == null) {
                System.out
                        .println("NullPoint exception , of reflection methodName="
                                + methodName);
                continue;
            }
            method.invoke(obj, entry.getValue());// 调用方法，set操作注意
        }
    }

    private static Object convertClxValue(Object value) {
        if (value instanceof String) {
            return String.valueOf(value);
        }
        return null;
    }
}

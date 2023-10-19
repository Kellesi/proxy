package com.test.junit;

import com.test.junit.anotation.AfterAll;
import com.test.junit.anotation.AfterMethod;
import com.test.junit.anotation.BeforeAll;
import com.test.junit.anotation.BeforeMethod;
import com.test.junit.anotation.Description;
import com.test.junit.anotation.Test;
import com.test.junit.anotation.TimeUnit;
import com.test.junit.anotation.Timeout;
import com.test.junit.assertion.AssertionsRuntimeException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

public class TestRunner {

    private final List<Class<?>> testClasses = new ArrayList<>();

    public void register(Class<?> testClass) {
        testClasses.add(testClass);
    }

    public void register(Class<?>... testClasses) {
        this.testClasses.addAll(Arrays.asList(testClasses));
    }

    public void run() {
        testClasses.forEach(TestRunner::processTest);
    }

    private static void processTest(Class<?> test) {

        Object instance = createInstance(test);
        Method[] methods = test.getMethods();
        List<Method> testMethods = findMethodByAnnotation(methods, Test.class);
        List<Method> beforeEachMethods = findMethodByAnnotation(methods, BeforeMethod.class);
        List<Method> beforeAllMethods = findMethodByAnnotation(methods, BeforeAll.class);
        List<Method> afterEachMethods = findMethodByAnnotation(methods, AfterMethod.class);
        List<Method> afterAllMethods = findMethodByAnnotation(methods, AfterAll.class);

        invokeMethods(instance, beforeAllMethods);
        invokeTestMethods(instance, beforeEachMethods, testMethods, afterEachMethods);
        invokeMethods(instance, afterAllMethods);
    }

    private static Object createInstance(Class<?> test) {
        try {
            Constructor<?> constructor = test.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Method> findMethodByAnnotation(Method[] methods, Class<? extends Annotation> annotation) {
        return Arrays.stream(methods)
                .filter(method -> method.isAnnotationPresent(annotation))
                .toList();
    }

    private static void invokeMethods(Object instance, List<Method> methods) {
        methods.forEach(method -> {
            try {
                method.invoke(instance);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void invokeTestMethods(Object instance, List<Method> beforeEachMethods,
                                          List<Method> methods, List<Method> afterEachMethods) {
        methods.forEach(method -> {
            try {
                method.setAccessible(true);
                invokeMethods(instance, beforeEachMethods);
                printDescription(method);
                invokeMethodTimed(instance, method);
                invokeMethods(instance, afterEachMethods);
                handleSunnyDayScenario(method);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof AssertionsRuntimeException) {
                    AssertionsRuntimeException ae = (AssertionsRuntimeException) e.getCause();
                    handleAssertionException(method, ae);
                }
            } catch (TimeoutException e) {
                handleTimedOutException(method, e.getMessage());
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void handleAssertionException(Method method, AssertionsRuntimeException e) {
        System.out.println(ConsoleColors.RED);
        System.out.println(String.format("[Test method %s] is failed. Expected = [%s]; actual = [%s]", method.getName(), e.getExpected(), e.getActual()));
        System.out.println(ConsoleColors.RESET);
    }

    private static void handleTimedOutException(Method method, String message) {
        System.out.println(ConsoleColors.RED);
        System.out.println(String.format("[Test method %s] is failed. Timed out. %s", method.getName(), message));
        System.out.println(ConsoleColors.RESET);
    }

    private static void handleSunnyDayScenario(Method method) {
        System.out.println(ConsoleColors.GREEN);
        System.out.println(String.format("[Test method %s] is successful", method.getName()));
        System.out.println(ConsoleColors.RESET);
    }

    private static void printDescription(Method method) {
        if (method.isAnnotationPresent(Description.class)) {
            Description description = method.getAnnotation(Description.class);
            System.out.print(String.format("[Test method %s] detailed description: %s", method.getName(), description.message()));
        }
    }

    private static void invokeMethodTimed(Object instance, Method method) throws InvocationTargetException, IllegalAccessException, TimeoutException {
        if (!method.isAnnotationPresent(Timeout.class)) {
            method.invoke(instance);
        } else {
            long targetTime = method.getAnnotation(Timeout.class).time();
            TimeUnit timeUnit = method.getAnnotation(Timeout.class).timeUnit();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                executor.submit(() -> {
                    try {
                        method.invoke(instance);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    } catch (InvocationTargetException e) {
                        if (e.getCause() instanceof AssertionsRuntimeException) {
                            AssertionsRuntimeException ae = (AssertionsRuntimeException) e.getCause();
                            handleAssertionException(method, ae);
                        }
                    }
                });
                if (!executor.awaitTermination(targetTime, timeUnit.getTimeUnit())) {
                    executor.shutdownNow();
                    throw new TimeoutException("Expected running time is " + targetTime + " " + timeUnit.name());
                }
            } catch (InterruptedException e) {
                throw new TimeoutException("Expected running time is " + targetTime + " " + timeUnit.name());
            }
        }
    }
}

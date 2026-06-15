package pkg.restoration.spring;

import java.util.Arrays;

import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class RestorationSpringContext {

    private static ConfigurableApplicationContext context;
    private static String[] launchArgs = new String[0];
    private static boolean shutdownHookRegistered;

    private RestorationSpringContext() {
    }

    public static synchronized void setLaunchArgs(String[] args) {
        launchArgs = args == null ? new String[0] : Arrays.copyOf(args, args.length);
    }

    public static synchronized ConfigurableApplicationContext start() {
        if (context == null) {
            context = new SpringApplicationBuilder(RestorationSpringApplication.class)
                    .web(WebApplicationType.NONE)
                    .headless(false)
                    .bannerMode(Banner.Mode.OFF)
                    .run(launchArgs);

            if (!shutdownHookRegistered) {
                Runtime.getRuntime().addShutdownHook(new Thread(RestorationSpringContext::close, "restoration-spring-shutdown"));
                shutdownHookRegistered = true;
            }
        }

        return context;
    }

    public static synchronized void close() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    public static void autowire(Object target) {
        start().getAutowireCapableBeanFactory().autowireBean(target);
    }

    public static <T> T getBean(Class<T> type) {
        return start().getBean(type);
    }
}

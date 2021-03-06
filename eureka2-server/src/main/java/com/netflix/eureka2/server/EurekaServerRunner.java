package com.netflix.eureka2.server;

import com.google.inject.Module;
import com.netflix.governator.LifecycleInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Tomasz Bak
 */
public abstract class EurekaServerRunner<S extends AbstractEurekaServer> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaServerRunner.class);

    private final Class<S> serverClass;
    protected final String name;
    protected final String serverName;

    protected LifecycleInjector injector;

    protected EurekaServerRunner(Class<S> serverClass) {
        this.name = null;
        this.serverName = getClass().getSimpleName() + '#' + getProcessPid();
        this.serverClass = serverClass;
    }

    protected EurekaServerRunner(String name, Class<S> serverClass) {
        this.name = name;
        this.serverName = name + '#' + getProcessPid();
        this.serverClass = serverClass;
    }

    public S getEurekaServer() {
        return injector.getInstance(serverClass);
    }

    public boolean start() {
        try {
            injector = createInjector();
        } catch (Exception e) {
            logger.error("Error while starting Eureka Write server.", e);
            return false;
        }
        logger.info("Container {} started", getClass().getSimpleName());
        return true;
    }

    public void shutdown() {
        if (injector != null) {
            injector.shutdown();
        }
    }

    public void awaitTermination() {
        try {
            injector.awaitTermination();
        } catch (InterruptedException e) {
            logger.info("Container {} shutting down", getClass().getSimpleName());
        }
    }

    protected abstract List<Module> getModules();

    protected abstract LifecycleInjector createInjector();

    protected List<Module> asList(Module... modules) {
        List<Module> toReturn = new ArrayList<>();
        for (Module m : modules) {
            toReturn.add(m);
        }
        return toReturn;
    }

    public String getProcessPid() {
        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        return rt.getName().substring(0, rt.getName().indexOf("@"));
    }
}

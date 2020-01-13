package org.javaprofile.bootman.controller;

import com.diffplug.common.base.Errors;
import com.google.common.collect.ImmutableList;
import com.sun.tools.attach.VirtualMachine;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.jboss.byteman.agent.install.Install;
import org.jboss.byteman.agent.submit.Submit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.util.function.Supplier;

@RestController
@Api(value="/byteman", tags={"Byteman API"}, produces ="application/json")
public class BytemanController {
    private static final Logger logger = LoggerFactory.getLogger(BytemanController.class);

    @RequestMapping(value="/activateAgent", method= RequestMethod.GET)
    @ApiOperation("activate Byteman agent with optional properties like org.jboss.byteman.verbose. You can use any properties from https://downloads.jboss.org/byteman/4.0.9/byteman-programmers-guide.html#environment-settings")
    public String activateAgent(@RequestParam(value="properties", required = false, defaultValue = "org.jboss.byteman.verbose") String[] properties) {
        ProcessInfo processInfo = isAgentActive();
        if(processInfo.isAttached()) {
            return "byteman agent is already activated in current process with pid: " + processInfo.getPid();
        }
        //activate byteman agent in the current VM with default port and provided properties
        try {
            Install.install(processInfo.getPid(), true, null, 0, properties);
        } catch (Throwable ex) {
            //byteman couldnt be picked up from classpath. Lets try loading it ourselves.
            logger.warn("falling back to manually load byteman agent jars as byteman default mechanism could not attach to pid: " + processInfo.getPid(), ex);
            installFromVM(processInfo.getPid(), true, null, 0, properties, false);
        }
        return "activated byteman agent in current process with pid: " + processInfo.getPid();
    }

    @RequestMapping(value="/listAllRules", method= RequestMethod.GET)
    @ApiOperation("list already installed Byteman Rules")
    public String listAllRules() {
        return execute(Errors.rethrow().wrap(doSubmit()::listAllRules));
    }

    @RequestMapping(value="/deleteAllRules", method= RequestMethod.GET)
    @ApiOperation("delete all Byteman Rules which are already installed")
    public String deleteAllRules() {
        return execute(Errors.rethrow().wrap(doSubmit()::deleteAllRules));
    }

    @RequestMapping(value="/addRules", method= RequestMethod.POST)
    @ApiOperation("add/update one or more Byteman Rules")
    public String addRules(@RequestBody String rule) {
        return execute(Errors.rethrow().wrap(() -> {
            File ruleFilePath = writeRuleToTempFile(rule);
            logger.info("rules are written in " + ruleFilePath);
            try {
                logger.info("adding rules from " + ruleFilePath);
                return doSubmit().addRulesFromFiles(ImmutableList.of(ruleFilePath.getAbsolutePath()));
            } finally {
                logger.info("Now deleting the rule file: " + ruleFilePath);
                ruleFilePath.delete();
            }
        }));
    }

    private String execute(Supplier<String> bytemanAction) {
        ProcessInfo processInfo = isAgentActive();
        //Only if byteman agent is active, execute the byteman action
        if(processInfo.isAttached()) {
            return bytemanAction.get();
        }
        //if byteman agent is not active, prompt to activate it first
        return activateFirstMessage(processInfo);
    }

    private File writeRuleToTempFile(String rule) {
        try{
            //create a temp file with name rule.btm
            File ruleFile = File.createTempFile("rule", ".btm");
            //write the rules inside rule.btm
            BufferedWriter bw = new BufferedWriter(new FileWriter(ruleFile));
            bw.write(rule);
            bw.close();
            return ruleFile;
        } catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    private String activateFirstMessage(ProcessInfo processInfo) {
        return "Please run activateAgent API first. byteman agent is not activated in current process with pid: " + processInfo.getPid();
    }

    private Submit doSubmit() {
        return new Submit(null, 0, null);
    }


    private ProcessInfo isAgentActive() {
        //get the processId of the current VM. In Java 8 world, this is perhaps the most portable way
        //to get the pid.
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        return new ProcessInfo(pid, Install.isAgentAttached(pid));
    }


    public static class ProcessInfo {
        private String pid;
        private boolean attached;

        public ProcessInfo(String pid, boolean attached) {
            this.pid = pid;
            this.attached = attached;
        }

        public String getPid() {
            return pid;
        }

        public boolean isAttached() {
            return attached;
        }
    }
    private void installFromVM(String pid, boolean addToBoot, String host, int port, String[] properties, boolean setPolicy) {
        String props = buildPropertyOption(properties);

        String agentOptions = "listener:true";
        if (host != null && host.length() != 0) {
            agentOptions += ",address:" + host;
        }
        if (port != 0) {
            agentOptions += ",port:" + port;
        }
        if (setPolicy) {
            agentOptions += ",policy:true";
        }
        //add properties Option
        agentOptions += props;

        VirtualMachine virtualMachine = null;
        //now attach the agent to the virtualMachine and load the agent
        try {
            final Class clazz = Class.forName("org.jboss.byteman.agent.Main");
            String agentJar = Paths.get(clazz.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            if (addToBoot) {
                agentOptions = agentOptions + ",boot:" + agentJar;
            }
            virtualMachine = VirtualMachine.attach(pid);
            logger.info("Trying to load with agentOptions: {}", agentOptions);
            virtualMachine.loadAgent(agentJar, agentOptions);
        }  catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            try {
                virtualMachine.detach();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private String buildPropertyOption(String[] properties) {
        //build the properties Option
        String props;
        if (properties != null) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < properties.length; i++) {
                builder.append(",prop:");
                builder.append(properties[i]);
            }
            props = builder.toString();
        } else {
            props = "";
        }
        return props;
    }
}

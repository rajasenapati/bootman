package org.javaprofile.bootman.controller;

import com.google.common.collect.ImmutableMap;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.web.bind.annotation.*;

import javax.script.*;
import java.util.*;

@RestController
@RequestMapping(value="/devConsole")
@Api(value="/devConsole", tags={"Developer Console Controller"},  produces ="application/json")
public class DevConsoleController implements ApplicationContextAware, ApplicationListener<ApplicationReadyEvent> {
    private static final Logger logger = LoggerFactory.getLogger(DevConsoleController.class);
    private final Map<Language, ScriptEngine> scriptingEngines;
    private ApplicationContext context;

    public DevConsoleController() {
        ScriptEngineManager manager = new ScriptEngineManager();
        // create a map of ScriptEngines with the key being the supported languages
        Map<Language, ScriptEngine> scriptingEngines = new LinkedHashMap<>();
        Arrays.stream(Language.values()).forEach(language -> {
            scriptingEngines.put(language, manager.getEngineByName(language.name()));
        });
        this.scriptingEngines = ImmutableMap.copyOf(scriptingEngines);
    }

    @ApiOperation("execute code written in one of the supported scripting languages")
    @RequestMapping(value = "/execute/{language}", method = RequestMethod.POST)
    public Object executeCode(@RequestBody String code, @PathVariable Language language) {
            try {
                logger.info("\nexecuting code in {}:\n#########\n{} \n#########\n", language, code);
                Object result = scriptingEngines.get(language).eval(code);
                logger.info("\nresult: \n#########\n{}\n#########\n", result);
                return result;
            } catch (Exception ex) {
                // fail the execution.
                logger.error("Error encountered while executing code " + code, ex);
                throw new RuntimeException(ex);
            }
    }

    @ApiOperation("get Context for a specified scripting language engine")
    @RequestMapping(value = "/context/{language}", method = RequestMethod.GET)
    public Set<String> getContext(@PathVariable Language language) {
        try {
            logger.info("get context for language {}", language);
            Bindings result = scriptingEngines.get(language).getContext().getBindings(ScriptContext.ENGINE_SCOPE);
            logger.info("result: " + result);
            return result.keySet();
        } catch (Exception ex) {
            // fail the execution.
            logger.error("Error encountered while finding context for language " + language, ex);
            throw new RuntimeException(ex);
        }
    }

    @ApiOperation("check if a key exists in the context for a specified scripting language engine")
    @RequestMapping(value = "/checkKeyPresentInContext/{language}/{key}", method = RequestMethod.GET)
    public String checkKeyPresentInContext(@PathVariable Language language, @PathVariable String key) {
        try {
            logger.info("get context for language {} and key {}", language, key);
            Object result = scriptingEngines.get(language).getContext().getBindings(ScriptContext.ENGINE_SCOPE).get(key);
            logger.info("result: " + result);
            return Objects.toString(result);
        } catch (Exception ex) {
            //fail the execution.
            logger.error("Error encountered while finding context for language " + language, ex);
            throw new RuntimeException(ex);
        }
    }


    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // get the list of all Spring beans once application has started up and then add these beans in the scripting engine context
        String[] allBeanNames = context.getBeanDefinitionNames();
        for(String beanName : allBeanNames) {
            try {
                //store the bean definition registry in all supported Scripting Engines
                Arrays.stream(Language.values()).forEach(language -> {this.scriptingEngines.get(language).put(beanName, context.getBean(beanName));});
            } catch (BeanCreationException ex) {
                logger.error("ignoring error  while trying to store the bean " + beanName);
            }
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        // store ApplicationContext reference to access required beans later on
        this.context = context;
    }

    // List of JSR-223 compliant supported languages in Dev Console. Additional scripting languages can be added by adding their
    // entries in pom.xml file.
    public enum Language {
        groovy,
        JavaScript;
    }
}

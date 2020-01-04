package org.javaprofile.bootman.controller;


import io.swagger.annotations.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Api(value="/greet", tags={"Sample Endpoint API"}, produces ="application/json")

@RequestMapping("/greet")
public class HelloController {
    private static final Logger logger = LoggerFactory.getLogger(HelloController.class);

    @RequestMapping(value="/{name}",method= RequestMethod.GET)
    public String sayHello(@PathVariable String name) {
        logger.info("just a harmless but useless log statement. I wish the name parameter were logged here.");
        String message = "hello " + name;
        return message;
    }
}

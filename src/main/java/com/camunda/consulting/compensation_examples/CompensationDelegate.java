package com.camunda.consulting.compensation_examples;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CompensationDelegate implements JavaDelegate {
  
  private static final Logger LOGGER = LoggerFactory.getLogger(CompensationDelegate.class);

  @Override
  public void execute(DelegateExecution execution) throws Exception {
    LOGGER.info("Do some compensation");
    Boolean shouldFail = (Boolean) execution.getVariable("shouldFail");
    if (shouldFail != null && shouldFail) {
      throw new ProcessEngineException("This should fail...");
    }
  }

}

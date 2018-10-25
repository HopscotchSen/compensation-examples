package com.camunda.consulting.compensation_examples;

import org.apache.ibatis.logging.LogFactory;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.junit4.SpringRunner;

import static org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions.assertThat;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.*;
import static org.junit.Assert.*;

/**
 * Test case starting an in-memory database-backed Process Engine.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
public class ProcessTest {

  private static final String PROCESS_DEFINITION_KEY = "compensation-examples";

  @Autowired
  private ProcessEngine processEngine;

  static {
    LogFactory.useSlf4jLogging(); // MyBatis
  }

  @Before
  public void setup() {
    init(processEngine);
  }

  @Test
  public void testCompensationWithMessage (){
    ProcessInstance processInstance = runtimeService().startProcessInstanceByKey("superCompensation");
    
    ProcessInstance subProcessInstance = processInstanceQuery().processDefinitionKey("basicCompensation").singleResult();
    assertThat(subProcessInstance).isWaitingAt("checkForSuccessTask");
    
    ProcessInstance intermediateProcessInstance = processInstanceQuery().processDefinitionKey("intermediateCompensation").singleResult();
    assertThat(intermediateProcessInstance).isWaitingAt("intermediateCallActivity");   
  
    runtimeService().createMessageCorrelation("compensation message").correlate();
    
    assertThat(intermediateProcessInstance).isWaitingAt("intermediateCallActivity");
    assertThat(processInstance).isWaitingAt("callActivity");
    assertThat(subProcessInstance).isWaitingAt("compensateTask", "subCompensationEvent2");
    
    execute(job(subProcessInstance));
    
    assertThat(subProcessInstance).isEnded().hasPassed("subEscalationEvent2"); 
    
    assertThat(processInstance).isWaitingAt("callActivity");
    assertThat(intermediateProcessInstance).isWaitingAt("compensateIntermediateTask", "intermediateCompensationEvent");
    
    complete(task(intermediateProcessInstance));
    
    assertThat(intermediateProcessInstance).isEnded().hasPassed("intermediateCompensationEvent"); 
  
    assertThat(processInstance).isWaitingAt("compensateLoggingTask", "compensationEvent");
    
    complete(task());
    
    assertThat(intermediateProcessInstance).hasPassed("intermediateEscalationEndEvent");
    assertThat(processInstance).isEnded().hasPassed("compensationCompletedEndEvent");
  }

  @Test
  public void testBasicCompensationHappyPath() {
   ProcessInstance processInstance = runtimeService().startProcessInstanceByKey("basicCompensation");
    
    // Now: Drive the process by API and assert correct behavior by camunda-bpm-assert
   assertThat(processInstance).isWaitingAt("checkForSuccessTask");
   complete(task(), withVariables("success", false));
   
   assertThat(processInstance).isWaitingAt("compensateTask");
   execute(job());
   
   assertThat(processInstance).isEnded().hasPassed("subEscalationEvent1", "compensateTask");
  }
  
  @Test
  public void testBasicCompensation_CompensationFailed() {
    ProcessInstance processInstance = runtimeService().startProcessInstanceByKey("basicCompensation");
    
    assertThat(processInstance).isWaitingAt("checkForSuccessTask");
    complete(task(), withVariables("success", false, "shouldFail", true));
    
    assertThat(processInstance).isActive().isWaitingAt("compensateTask");
    try {
      execute(job());
    } catch (ProcessEngineException e) {
      assertThat(e).hasMessage("This should fail...");
    }
    
    assertThat(processInstance).isActive().isWaitingAt("compensateTask", "subCompensationEvent1");
    runtimeService().setVariable(processInstance.getId(), "shouldFail", false);
    
    execute(job());
    
    assertThat(processInstance).isEnded().hasPassed("compensateTask");
  }
  
  @Test
  public void testCompensationInSubProcesses() {
    ProcessInstance processInstance = runtimeService().startProcessInstanceByKey("superCompensation");
    
    assertThat(processInstance).isWaitingAt("callActivity");
    
    ProcessInstance subProcessInstance = processInstanceQuery().processDefinitionKey("basicCompensation").singleResult();
    assertThat(subProcessInstance).isWaitingAt("checkForSuccessTask");
    
    complete(task(), withVariables("success", false));
    
    assertThat(subProcessInstance).isWaitingAt("compensateTask");
    execute(job());
    
    assertThat(subProcessInstance).isEnded().hasPassed("subEscalationEvent1");
    
    ProcessInstance intermediateProcessInstance = processInstanceQuery().processDefinitionKey("intermediateCompensation").singleResult();
    
    assertThat(intermediateProcessInstance).isWaitingAt("compensateIntermediateTask");
    complete(task());
    
    assertThat(processInstance).isWaitingAt("compensateLoggingTask", "compensationEvent");
    
    complete(task());
    
    assertThat(processInstance).isEnded();
  }
  
}

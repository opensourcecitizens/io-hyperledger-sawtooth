package io.mtini.sawtooth;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sawtooth.sdk.processor.TransactionProcessor;



public class MyTransactionProcessor {
    private final static Logger logger = LoggerFactory.getLogger(MyTransactionProcessor.class.getName());

    public static void main(String[] args) {
	//Check connection string to validator is passed in arguments.
	if (args.length != 1) {
	    logger.info("Missing argument!! Please pass validator connection string");
	}
	// Connect to validator with connection string (tcp://validator:4004)
	logger.info("argument 1 "+args[0]);
	TransactionProcessor simpleProcessor = new TransactionProcessor(args[0]);
	// Create simple wallet transaction handler and register with the validator
	simpleProcessor.addHandler(new SimpleJournalAndLedgerHandler());
	//simpleProcessor.addHandler(new XoHandler());
	Thread thread = new Thread(simpleProcessor);
	//start the transaction processor
	thread.run();
    }

}




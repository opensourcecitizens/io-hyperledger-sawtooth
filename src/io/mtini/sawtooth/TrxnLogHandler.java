package io.mtini.sawtooth;

import java.awt.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import sawtooth.sdk.processor.State;
import sawtooth.sdk.processor.TransactionHandler;
import sawtooth.sdk.processor.Utils;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.protobuf.TpProcessRequest;
import sawtooth.sdk.protobuf.TransactionHeader;

public class TrxnLogHandler implements TransactionHandler {

	public static final String txnFamilyName = "LogTransactionHandler";
	public static final String version = "1.0";
	
	private String trxnLogNamespace = null;
	
	{
		try {
		    //Initialize the simple wallet name space using first 6 characters
			trxnLogNamespace = Utils.hash512(txnFamilyName.getBytes("UTF-8")).substring(0, 6);
		} catch (java.io.UnsupportedEncodingException ex) {
		    System.err.println("Unsupported the encoding format ");
		    ex.printStackTrace();
		    trxnLogNamespace="";
		    System.exit(1);
		}
	}
		
	
	@Override
	public void apply(TpProcessRequest request, State stateInfo)
			throws InvalidTransactionException, InternalError {
		
		 String userKey;
		 TransactionHeader header = request.getHeader();
		 userKey = header.getSignerPublicKey();
		
		String payload =  request.getPayload().toStringUtf8();

        // Split the csv utf-8 string
		ArrayList<String> payloadList = new ArrayList<>(Arrays.asList(payload.split(",")));
		if(payloadList.size() != 2 && !(payloadList.size() == 3 && "transfer".equals(payloadList.get(0)))) {
			throw new InvalidTransactionException("Invalid no. of arguments: expected 2 or 3, got:" + payloadList.size());
		}

		// First argument from payload is operation name
		String operation = payloadList.get(0);
		// Get the amount
		Integer amount = Integer.valueOf(payloadList.get(1));
		/*
		switch(operation) {
		case "debit" :
		    makeDeposit(stateInfo, operation, amount, userKey);
		    break;
		case "credit":
		    makeWithdraw(stateInfo, operation, amount, userKey);
		    break;
		case "transfer":
			makeTransfer(stateInfo, operation, amount, payloadList.get(2), userKey);
			break;
		default:
		    String error = "Unsupported operation " + operation;
		    throw new InvalidTransactionException(error);
		}
		*/

	}

	@Override
	public Collection<String> getNameSpaces() {

		ArrayList<String> ret = new ArrayList<String>();
		ret.add(trxnLogNamespace);
		return ret;
	}

	@Override
	public String getVersion() {

		return version;
	}

	@Override
	public String transactionFamilyName() {

		return trxnLogNamespace+version;
	}
	
}

	/*** Data objects - model ***/
	interface JournalAccountType{
		public String toString();
	}
	
	enum LeftAccounts implements JournalAccountType{
		dividend,expense,assett;
		public String toString(){
			return "LeftAccounts:"+this.name();
		}
	}
	
	enum RightAccounts implements JournalAccountType{
		liability,equity,revenue;
		public String toString(){
			return "RightAccounts:"+this.name();
		}
	}
	
	enum JournalEntry{
		
	}
	
	enum AccountAction{
		 debit,credit;
		
		LedgerData process(Double _balance, JournalAccountType _acnt,JournalAccountType _acnt2) throws InvalidTransactionException{
			acnt = _acnt; acnt2 = _acnt2;
			balance = _balance;
			
			if(_acnt == null || _acnt2 ==null){
				throw new InvalidTransactionException("Failed to process "+_acnt.toString()+";"+_acnt2.toString() +" Due to null value");
			}else if(_acnt instanceof RightAccounts && _acnt2 instanceof LeftAccounts ){
				switch(this.name()){
				case "debit": reduce(balance, acnt,acnt2);
				case "credit":add(balance, acnt,acnt2);
				}
			}else{
				throw new InvalidTransactionException("Failed to process "+_acnt.toString()+";"+_acnt2.toString() );
			}
			
			return null;
			}
		
		 private JournalAccountType acnt;
		 private JournalAccountType acnt2;
		 private Double balance;
		 
		 private Double reduce(Double balance, JournalAccountType _acnt,JournalAccountType _acnt2){
				
				return 0.0;
		}
		
		 private Double add(Double balance,JournalAccountType _acnt,JournalAccountType _acnt2){
				
				return 0.0;
		}
		 
	}
	
	class JournalData {
		   final String gameName;
		   final String action;
		   final String space;

		   JournalData(String gameName, String action, String space) {
		     this.gameName = gameName;
		     this.action = action;
		     this.space = space;
		   }
	}
	
	class LedgerData {
		   final String gameName;
		   final String action;
		   final String space;

		   LedgerData(String gameName, String action, String space) {
		     this.gameName = gameName;
		     this.action = action;
		     this.space = space;
		   }
	}



package io.mtini.sawtooth;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import io.mtini.proto.CustomerAccountProtos;
import io.mtini.proto.CustomerAccountProtos.LedgerEntries;

import sawtooth.sdk.processor.State;
import sawtooth.sdk.processor.TransactionHandler;
import sawtooth.sdk.processor.Utils;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.protobuf.TpProcessRequest;

public class AuditJournalAndLedgerHandler implements TransactionHandler {
	private final Logger logger = LoggerFactory.getLogger(AuditJournalAndLedgerHandler.class.getName());
	private final static String version = "1.0";
	private final static String familyName = "adt";
	private String simpleNamespace;

	AuditJournalAndLedgerHandler() {
		logger.info("Initializing SimpleJournalAndLedgerHandler");
		try {
			//Initialize the simple wallet name space using first 6 characters
			simpleNamespace = Utils.hash512(familyName.getBytes("UTF-8")).substring(0, 6);
		} catch (java.io.UnsupportedEncodingException ex) {
			System.err.println("Unsupported the encoding format ");
			ex.printStackTrace();
			logger.error(ex.getMessage());
			System.exit(1);
		}
	}

	/*
	 * 
	 * Assumes request payload is journaled data, 
	 *TODO save all transactions in the ledger but do the validation after for notification purposes. 
	 *
	 */
	
	Errors errors = null;

	@Override
	public void apply(TpProcessRequest request, State stateInfo) throws InvalidTransactionException, InternalError {
		logger.info("apply method is up");
		errors = Errors.instance();
		//try{
		// Extract the payload as utf8 str from the transaction, in request var
		String payload =  request.getPayload().toStringUtf8();

		// Split the csv utf-8 string
		ArrayList<String> payloadList = decodeData(payload);
		//expects left side credit/debit, left account_type, right account_type; amount - assumes business rules are made independent of this application
		if(payloadList.size() != 5 ) {
			//throw new InvalidTransactionException("Invalid no. of arguments: expected 4, got:" + payloadList.size());
			errors.addError(new InvalidTransactionException("Invalid no. of arguments: expected 4, got:" + payloadList.size()));
		}

		// First argument from payload is operation name
		String operation = payloadList.get(0);
		// left account
		String l_accntType = payloadList.get(1);
		// right account
		String r_accntType = payloadList.get(2);
		// Get the amount
		Double amount = Double.valueOf(payloadList.get(3));
		
		//2018-10-07 22:49:32.206891
		long dateTime = -1;
		try {
			dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(payloadList.get(4)).getTime();
		} catch (ParseException e1) {
			logger.error(e1.getMessage(),e1);
			errors.addError(e1);
		}
		//long dateTime = Date.parse(payloadList.get(4));
		// Get the user signing public key from header
		String customerAcctKey = request.getHeader().getSignerPublicKey();

		try {
			validateAccountTypes( l_accntType,  r_accntType);
		} catch (DataValidationException e) {
			errors.addError(e);
		}


		updateJournalAndLedger(stateInfo, operation, l_accntType, r_accntType, amount, customerAcctKey, dateTime);

		
		logger.warn(errors.toString());


		//}catch(InvalidTransactionException e){
		//
		//}catch (DataValidationException e) {
		//
		//}
		

	}



	private ArrayList<String> decodeData(String payload) {
		logger.info("decoding data");

		// Split the csv utf-8 string
		ArrayList<String> payloadList = new ArrayList<>(Arrays.asList(payload.split(",")));

		return payloadList;

	}

	private void validateAccountTypes(String l_accntType, String r_accntType)  throws DataValidationException {
		//validate  (left): dividend , expense or asset
		//validate credit (right): liability , equity , revenue
		List<String> leftTypes = Arrays.asList("dividend" , "expense","asset");
		List<String> rightTypes = Arrays.asList("liability" , "equity" , "revenue");
		StringBuilder errors = new StringBuilder();
		if(!StringUtils.isNotEmpty(l_accntType) || !leftTypes.contains(l_accntType)){
			errors.append(l_accntType+" is not a left accountType");
		}

		if(!StringUtils.isNotEmpty(r_accntType) || !rightTypes.contains(r_accntType)){
			if(errors.length()>0){errors.append("\n");}
			errors.append(r_accntType+" is not a right accountType");
		}

		if(errors.length()>0){throw new DataValidationException(errors.toString());}
	}

	/***
	 * 1. from state Get ledger of current key/user
	 * 		a. if null - create default an 0.0 balance
	 *		b. if not null return
	 * 2. Determine trxn type and alter balance
	 * 
	 * 
	 * @param stateInfo
	 * @param operation
	 * @param l_accntType
	 * @param r_accntType
	 * @param amount
	 * @param accountKey
	 * @throws InvalidTransactionException
	 * @throws InternalError
	 */
	private void updateJournalAndLedger(State stateInfo, String operation, String l_accntType, String r_accntType, Double amount, String accountKey, long dateTime)
			throws InvalidTransactionException, InternalError {

		// Get the wallet key derived from the wallet user's public key
		// String acntJournalKey = getAccountJournalKey(accountKey);
		String acntLedgerKey = getAccountLedgerKey(accountKey);

		logger.info("Got accountKey key " + accountKey + " acntRecordKey key " + acntLedgerKey);
		// Get ledger of current key/user
		Map<String, ByteString> currentLedgerEntry = null;
		try{
			currentLedgerEntry = stateInfo.getState(Collections.singletonList(acntLedgerKey));
			
		}catch(InvalidTransactionException e){
			logger.warn(e.getLocalizedMessage(),e);
			if(StringUtils.contains(e.getMessage(),"Tried to get unauthorized address")){
				logger.info("Creating new currentLedgerEntry");
				currentLedgerEntry = Collections.singletonMap(acntLedgerKey, CustomerAccountProtos.LedgerEntries.getDefaultInstance().toByteString());//emptyMap();
			}else 
				throw e;
		}
		
		//json to map
		//ByteString currentAccountLedger = currentLedgerEntry.getOrDefault(acntLedgerKey, CustomerAccountProtos.LedgerEntries.getDefaultInstance().toByteString());
		ByteString currentAccountLedger = currentLedgerEntry.get(acntLedgerKey);

		// create a map of entries for the ledger:
		CustomerAccountProtos.LedgerEntries ledgerEntries = null;
		try {
			ledgerEntries = currentAccountLedger==null
					?CustomerAccountProtos.LedgerEntries.getDefaultInstance():
						CustomerAccountProtos.LedgerEntries.parseFrom( currentAccountLedger.toByteArray()  );
		} catch (InvalidProtocolBufferException e) {
			errors.addError(e);
			logger.error(e.getLocalizedMessage(),e);
		}
		
		//update ledger data
		int entryCount = ledgerEntries.getLedgerEntryCount();
		logger.info("Ledger entry count = "+entryCount);
		//ledgerEntries = ledgerEntries.getLedgerEntryCount()==0?CustomerAccountProtos.LedgerEntries.getDefaultInstance():ledgerEntries;	
		
		CustomerAccountProtos.LedgerEntries.LedgerData ledgerEntry = entryCount==0?CustomerAccountProtos.LedgerEntries.LedgerData.getDefaultInstance():ledgerEntries.getLedgerEntry(entryCount-1);
	
		int journalCount = ledgerEntry.getJournalCount();
		logger.info("journal count = "+journalCount);
		//CustomerAccountProtos.LedgerEntries.LedgerData.JournalData journalData = ledgerEntry!=null && journalCount>0?ledgerEntry.getJournal(journalCount-1):CustomerAccountProtos.LedgerEntries.LedgerData.JournalData.getDefaultInstance();
		CustomerAccountProtos.LedgerEntries.LedgerData.JournalData.Builder leftAcntJrnlBuilder = CustomerAccountProtos.LedgerEntries.LedgerData.JournalData.getDefaultInstance().toBuilder();//journalData.toBuilder().mergeFrom(journalData);
		leftAcntJrnlBuilder.setDate(dateTime);
		leftAcntJrnlBuilder.setNotes(l_accntType);
				
		CustomerAccountProtos.LedgerEntries.LedgerData.JournalData.Builder rightAcntJrnlBuilder = CustomerAccountProtos.LedgerEntries.LedgerData.JournalData.getDefaultInstance().toBuilder();//journalData.toBuilder().mergeFrom(journalData);
		rightAcntJrnlBuilder.setDate(dateTime);	
		rightAcntJrnlBuilder.setNotes(r_accntType);
		
		CustomerAccountProtos.LedgerEntries.LedgerData.Builder ledgerBuilder = ledgerEntry.toBuilder().mergeFrom(ledgerEntry);

		CustomerAccountProtos.LedgerEntries.Builder ledgerEntriesBuilder = ledgerEntries.toBuilder().mergeFrom(ledgerEntries);
		
		Double previousBalance = ledgerBuilder.getBalance();
		logger.info("previousBalance = "+previousBalance);
		ledgerBuilder.getAccountName();
		//ledgerBuilder.setAccountName(value);
		ledgerBuilder.setLeftAccount(l_accntType);
		ledgerBuilder.setRightAccount(r_accntType);
		
		Double newBalance = 0.0;
		
		CustomerAccountProtos.AccountType action = null;
		
		switch(operation) {
		case "credit" :
			action = CustomerAccountProtos.AccountType.CREDIT;
			leftAcntJrnlBuilder.setAccountType(action);
			leftAcntJrnlBuilder.setCreditAmount(amount);
			rightAcntJrnlBuilder.setDebitAmount(amount);
			newBalance = previousBalance + amount;
			break;
		case "debit":
			action = CustomerAccountProtos.AccountType.DEBIT;
			leftAcntJrnlBuilder.setAccountType(action);
			leftAcntJrnlBuilder.setDebitAmount(amount);
			rightAcntJrnlBuilder.setCreditAmount(amount);
			newBalance = previousBalance - amount;
			break;
		default:
			String error = "Unsupported operation " + operation;
			errors.addError( new InvalidTransactionException(error));
			logger.warn(error);
		}

		logger.info("newBalance = "+newBalance);
		ledgerBuilder.setAmount(amount);
		ledgerBuilder.setBalance(newBalance);
		ledgerBuilder.setAction(action);
		ledgerBuilder.addJournal(leftAcntJrnlBuilder.build());
		ledgerBuilder.addJournal(rightAcntJrnlBuilder.build());
		
		ledgerEntriesBuilder.addLedgerEntry(ledgerBuilder.build());

		CustomerAccountProtos.LedgerEntries updateLedgerEntries = ledgerEntriesBuilder.build();
		acntLedgerKey = getAccountLedgerKey(accountKey);
		
		logger.debug(updateLedgerEntries.toString());
		
		Map.Entry<String, ByteString> ledgerEntryByteMap = new AbstractMap.SimpleEntry<String, ByteString>(acntLedgerKey,
				ByteString.copyFrom( updateLedgerEntries.toByteArray() ));
		
		Collection<Map.Entry<String, ByteString>> newLedgerEntryByteStr = Collections.singletonList(ledgerEntryByteMap);
		
		
		trialBalance(updateLedgerEntries);

		Collection<String> res = stateInfo.setState(newLedgerEntryByteStr);
		
		logger.info("Response : "+res);
	}
	
	private void updateJournalAndLedger1(State stateInfo, String operation, String l_accntType, String r_accntType, Double amount, String accountKey, long dateTime)
			throws InvalidTransactionException, InternalError {

		// Get the wallet key derived from the wallet user's public key
		// String acntJournalKey = getAccountJournalKey(accountKey);
		String acntLedgerKey = getAccountLedgerKey(accountKey);

		logger.info("Got accountKey key " + accountKey + " acntRecordKey key " + acntLedgerKey);
		// Get ledger of current key/user
		Map<String, ByteString> currentLedgerEntry = null;
		try{
			currentLedgerEntry = stateInfo.getState(Collections.singletonList(acntLedgerKey));
			
		}catch(InvalidTransactionException e){
			logger.warn(e.getLocalizedMessage(),e);
			if(StringUtils.contains(e.getMessage(),"Tried to get unauthorized address")){
				logger.info("Creating new currentLedgerEntry");
				currentLedgerEntry = Collections.singletonMap(acntLedgerKey, CustomerAccountProtos.LedgerEntries.getDefaultInstance().toByteString());//emptyMap();
			}else 
				throw e;
		}
		
		//json to map
		//ByteString currentAccountLedger = currentLedgerEntry.getOrDefault(acntLedgerKey, CustomerAccountProtos.LedgerEntries.getDefaultInstance().toByteString());
		ByteString currentAccountLedger = currentLedgerEntry.get(acntLedgerKey);

		// create a map of entries for the ledger:
		CustomerAccountProtos.LedgerEntries ledgerEntries = null;
		try {
			ledgerEntries = currentAccountLedger==null
					?CustomerAccountProtos.LedgerEntries.getDefaultInstance():
						CustomerAccountProtos.LedgerEntries.parseFrom( currentAccountLedger.toByteArray()  );
		} catch (InvalidProtocolBufferException e) {
			errors.addError(e);
			logger.error(e.getLocalizedMessage(),e);
		}
		
		//update ledger data
		int entryCount = ledgerEntries.getLedgerEntryCount();
		logger.info("Ledger entry count = "+entryCount);
		//ledgerEntries = ledgerEntries.getLedgerEntryCount()==0?CustomerAccountProtos.LedgerEntries.getDefaultInstance():ledgerEntries;	
		
		CustomerAccountProtos.LedgerEntries.LedgerData ledgerEntry = entryCount==0?CustomerAccountProtos.LedgerEntries.LedgerData.getDefaultInstance():ledgerEntries.getLedgerEntry(entryCount-1);
	
		int journalCount = ledgerEntry.getJournalCount();
		logger.info("journal count = "+journalCount);
		//CustomerAccountProtos.LedgerEntries.LedgerData.JournalData journalData = ledgerEntry!=null && journalCount>0?ledgerEntry.getJournal(journalCount-1):CustomerAccountProtos.LedgerEntries.LedgerData.JournalData.getDefaultInstance();
		CustomerAccountProtos.LedgerEntries.LedgerData.JournalData.Builder leftAcntJrnlBuilder = CustomerAccountProtos.LedgerEntries.LedgerData.JournalData.getDefaultInstance().toBuilder();//journalData.toBuilder().mergeFrom(journalData);
		leftAcntJrnlBuilder.setDate(dateTime);
		leftAcntJrnlBuilder.setNotes(l_accntType);
				
		CustomerAccountProtos.LedgerEntries.LedgerData.JournalData.Builder rightAcntJrnlBuilder = CustomerAccountProtos.LedgerEntries.LedgerData.JournalData.getDefaultInstance().toBuilder();//journalData.toBuilder().mergeFrom(journalData);
		rightAcntJrnlBuilder.setDate(dateTime);	
		rightAcntJrnlBuilder.setNotes(r_accntType);
		
		CustomerAccountProtos.LedgerEntries.LedgerData.Builder ledgerBuilder = ledgerEntry.toBuilder().mergeFrom(ledgerEntry);

		CustomerAccountProtos.LedgerEntries.Builder ledgerEntriesBuilder = ledgerEntries.toBuilder().mergeFrom(ledgerEntries);
		
		Double previousBalance = ledgerBuilder.getBalance();
		logger.info("previousBalance = "+previousBalance);
		ledgerBuilder.getAccountName();
		//ledgerBuilder.setAccountName(value);
		ledgerBuilder.setLeftAccount(l_accntType);
		ledgerBuilder.setRightAccount(r_accntType);
		
		Double newBalance = 0.0;
		
		CustomerAccountProtos.AccountType action = null;
		
		switch(operation) {
		case "credit" :
			action = CustomerAccountProtos.AccountType.CREDIT;
			leftAcntJrnlBuilder.setAccountType(action);
			leftAcntJrnlBuilder.setCreditAmount(amount);
			rightAcntJrnlBuilder.setDebitAmount(amount);
			newBalance = previousBalance + amount;
			break;
		case "debit":
			action = CustomerAccountProtos.AccountType.DEBIT;
			leftAcntJrnlBuilder.setAccountType(action);
			leftAcntJrnlBuilder.setDebitAmount(amount);
			rightAcntJrnlBuilder.setCreditAmount(amount);
			newBalance = previousBalance - amount;
			break;
		default:
			String error = "Unsupported operation " + operation;
			errors.addError( new InvalidTransactionException(error));
			logger.warn(error);
		}

		logger.info("newBalance = "+newBalance);
		ledgerBuilder.setAmount(amount);
		ledgerBuilder.setBalance(newBalance);
		ledgerBuilder.setAction(action);
		ledgerBuilder.addJournal(leftAcntJrnlBuilder.build());
		ledgerBuilder.addJournal(rightAcntJrnlBuilder.build());
		
		ledgerEntriesBuilder.addLedgerEntry(ledgerBuilder.build());

		CustomerAccountProtos.LedgerEntries updateLedgerEntries = ledgerEntriesBuilder.build();
		acntLedgerKey = getAccountLedgerKey(accountKey);
		
		logger.debug(updateLedgerEntries.toString());
		
		Map.Entry<String, ByteString> ledgerEntryByteMap = new AbstractMap.SimpleEntry<String, ByteString>(acntLedgerKey,
				ByteString.copyFrom( updateLedgerEntries.toByteArray() ));
		
		Collection<Map.Entry<String, ByteString>> newLedgerEntryByteStr = Collections.singletonList(ledgerEntryByteMap);
		
		
		trialBalance(updateLedgerEntries);

		Collection<String> res = stateInfo.setState(newLedgerEntryByteStr);
		
		logger.info("Response : "+res);
	}
	
	private void trialBalance(LedgerEntries updateLedgerEntries) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Collection<String> getNameSpaces() {
		ArrayList<String> namespaces = new ArrayList<>();
		namespaces.add(simpleNamespace);
		return namespaces;
	}

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public String transactionFamilyName() {
		return familyName;
	}

	private String getAccountLedgerKey(String accountKey) {

		return Utils.hash512(familyName.getBytes()).substring(0, 6)
				+ Utils.hash512(accountKey.getBytes()).substring(0, 64);
	}

	class DataValidationException extends Exception{


		/**
		 * 
		 */
		private static final long serialVersionUID = -737643999241781440L;

		public DataValidationException(String msg){
			super(msg);
		}
	}

	class CalculationException extends Exception{
		/**
		 * 
		 */
		private static final long serialVersionUID = -6507138089195687925L;

		public CalculationException(String msg){
			super(msg);
		}
	}

	class OperationException extends Exception{
		/**
		 * 
		 */
		private static final long serialVersionUID = 8228651379764144830L;

		public OperationException(String msg){
			super(msg);
		}
	}
	
	class TrialBalanceException extends Exception{
		/**
		 * 
		 */
		private static final long serialVersionUID = -7797457253060138282L;

		public TrialBalanceException(String msg){
			super(msg);
		}
	}

}

class Errors{
	private int count = 0;
	private StringBuilder errors = new StringBuilder();

	public static Errors instance(){
		return new Errors();
	}

	public int addError(Exception e){
		if(e!=null){

			if(count>0){
				errors.append("\n");
			}
			errors.append(e.toString());
			count++;
		}
		return count;
	}
	
	public String toString(){
		return errors.length()==0?"None!":errors.toString();
	}
}



/*//Model
class JournalData {
	   final Date date;
	   final String accountType;
	   final double debitAmount;
	   final double creditAmount;
	   final String notes;

	   JournalData(Date date, String accountType, double debitAmount, double creditAmount, String notes) {

	     this.date = date;
	     this.accountType = accountType;
	     this.debitAmount = debitAmount;
	     this.creditAmount = creditAmount;
	     this.notes = notes;

	   }
}

class LedgerData {
	   final JournalData journal;
	   final String accountName;
	   final String leftAccount;
	   final String rightAccount;
	   final String action;//debit or credit
	   final double amount;
	   final double balance;

	   LedgerData(JournalData journal, String accountName, String leftAccount, String rightAccount, String action, double amount , double balance) {
	     this.journal = journal;
	     this.accountName = accountName;
	     this.leftAccount = leftAccount;
	     this.rightAccount = rightAccount;
	     this.action = action;
	     this.amount = amount;
	     this.balance = balance;
	   }

	   public static LedgerData defaultValue(){
		   return new LedgerData(new JournalData(new Date(), "", 0.0, 0.0, ""), "", "", "", "", 0.0, 0.0);
	   }
}*/

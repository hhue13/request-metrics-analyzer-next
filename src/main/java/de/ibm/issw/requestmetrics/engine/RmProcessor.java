package de.ibm.issw.requestmetrics.engine;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.ibm.issw.requestmetrics.engine.events.LogParsingTypeEvent;
import de.ibm.issw.requestmetrics.engine.events.UnsupportedFileEvent;
import de.ibm.issw.requestmetrics.model.RMComponent;
import de.ibm.issw.requestmetrics.model.RMNode;
import de.ibm.issw.requestmetrics.model.RMRecord;
import de.ibm.issw.requestmetrics.model.RmRootCase;
import de.ibm.issw.requestmetrics.util.StringPool;

public class RmProcessor extends Observable implements Processor{
	// Logging and utilities
	private static final Logger LOG = Logger.getLogger(RmProcessor.class.getName());
	private static final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yy H:m:s:S z", Locale.US);

	// Parsing 
	private static final String REGEX_GREPPED = "([^:]*):\\[([^\\]]*)\\] (\\w+) PmiRmArmWrapp I\\s+PMRM0003I:\\s+parent:ver=(\\d),ip=([^,]+),time=([^,]+),pid=([^,]+),reqid=([^,]+),event=(\\w+)\\s-\\scurrent:ver=([^,]+),ip=([^,]+),time=([^,]+),pid=([^,]+),reqid=([^,]+),event=(.*) type=(.*) detail=(.*) elapsed=(\\w+)";	
	private static final String REGEX_RAW = "\\[([^\\]\\*]*)\\] (\\w{8}) PmiRmArmWrapp I\\s+PMRM0003I:\\s+parent:ver=(\\d),ip=([^,]+),time=([^,]+),pid=([^,]+),reqid=([^,]+),event=(\\w+)\\s-\\scurrent:ver=([^,]+),ip=([^,]+),time=([^,]+),pid=([^,]+),reqid=([^,]+),event=(.*) type=(.*) detail=(.*) elapsed=(\\w+)";	
	private static final Pattern PATTERN_GREPPED = Pattern.compile(REGEX_GREPPED);
	private static final Pattern PATTERN_RAW = Pattern.compile(REGEX_RAW);
	private static final String UNKNOWN = "UNKNOWN";
	private String lastParsingType = LogParsingTypeEvent.TYPE_UNKNOWN;

	// Internal data structures
	private final Map<Long, RMNode> allNodes = new HashMap<Long, RMNode>();
	private final List<RmRootCase> rootCases = new ArrayList<RmRootCase>();
	private final Set<String> rootCaseTypes = new TreeSet<String>();
	
	private FileHandler fileHandler;
	
	public RmProcessor() {
		fileHandler = new FileHandler(this);
	}
	
	public void processInputFiles(File[] files) {
		// delegate to the file handler
		fileHandler.processInputFiles(files);
		
		if(getRootCases().size() > 0) {
			LOG.info("Number of testCase tables found: " + getRootCases().size());
		}
	}
	
	@Override
	public void beforeSingleFileParsed(File file) {
		this.lastParsingType = LogParsingTypeEvent.TYPE_UNKNOWN;
	}

	@Override
	public void afterSingleFileParsed(File file) {
		if (rootCases.size() == 0 && allNodes.size() == 0){
			//notify observers that the file can not be processed because no metrics data was found
			setChanged();
			notifyObservers(new UnsupportedFileEvent(this, file.getName()));
		}
	}
	
	@Override
	public void handleLine(String line, File file) {
		final RMRecord record = processSingleLine(line, file);
		if(record != null) {
			// process the record 
			addRmRecordToDataset(record);
		}
	}
	
	/**
	 * processes a single line and creates the java record representation out of it
	 * @param line the currently processing line
	 * @return RMRecord the record that has been generated or null if the line is not a valid log statement
	 * 
	 */
	private RMRecord processSingleLine(String line, File file) {
		RMRecord record = null;
		
		// parse the log using a REGEX, Strings are processed by a string pool
		final Matcher logMatcherGrepped = PATTERN_GREPPED.matcher(line);
		final Matcher logMatcherRaw = PATTERN_RAW.matcher(line);
		
		String currentParsingType = LogParsingTypeEvent.TYPE_UNKNOWN;
		int groupNr = 1;
		String logFileName = null;
		Matcher currentMatcher = null;
				
		//check, which type of file was loaded and by that, which logMatcher, Pattern & Regex has to be used
		if(logMatcherGrepped.matches()) {
			currentParsingType = LogParsingTypeEvent.TYPE_GREPPED;
			currentMatcher = logMatcherGrepped;
			logFileName = StringPool.intern(logMatcherGrepped.group(groupNr++));
		} 
		else if (logMatcherRaw.matches()) {
			currentParsingType = LogParsingTypeEvent.TYPE_RAW;
			currentMatcher = logMatcherRaw;
			logFileName = file.getName();
		}
		
		//check if the parsing type changed and notify observers if it has
		if (parsingTypeHasChanged(currentParsingType, lastParsingType)) {
			setChanged();
			notifyObservers(new LogParsingTypeEvent(this, file.getName(), currentParsingType));
		}
		
		if(!currentParsingType.equals(LogParsingTypeEvent.TYPE_UNKNOWN)) lastParsingType = currentParsingType;
		
		if (currentMatcher != null) {
			final String timestamp = StringPool.intern(currentMatcher.group(groupNr++));
			final String threadId = StringPool.intern(currentMatcher.group(groupNr++));
			
			final Integer parentVersion = Integer.parseInt(currentMatcher.group(groupNr++));
			final String parentIp = StringPool.intern(currentMatcher.group(groupNr++));
			final Long parentTimestamp = Long.parseLong(currentMatcher.group(groupNr++));
			final Long parentPid = Long.parseLong(currentMatcher.group(groupNr++));
			final Long parentRequestId = Long.parseLong(currentMatcher.group(groupNr++));
			final String parentEvent = StringPool.intern(currentMatcher.group(groupNr++));
			
			final Integer currentVersion = Integer.parseInt(currentMatcher.group(groupNr++));
			final String currentIp = StringPool.intern(currentMatcher.group(groupNr++));
			final Long currentTimestamp = Long.parseLong(currentMatcher.group(groupNr++));
			final Long currentPid = Long.parseLong(currentMatcher.group(groupNr++));
			final Long currentRequestId = Long.parseLong(currentMatcher.group(groupNr++));
			final String currentEvent = StringPool.intern(currentMatcher.group(groupNr++));
			final String type = StringPool.intern(currentMatcher.group(groupNr++));
			final String detail = StringPool.intern(currentMatcher.group(groupNr++));
			final Long currentElapsed = Long.parseLong(currentMatcher.group(groupNr++));
			
			Date recordDate = null; //[5/28/15 11:10:39:507 EDT]
			try {
				recordDate = sdf.parse(timestamp);
			} catch (ParseException e) {
				e.printStackTrace();
				LOG.severe("could not parse the log timestamp: " + timestamp);
			}
			
			final RMComponent currentCmp = new RMComponent(currentVersion, currentIp, currentTimestamp, currentPid, currentRequestId, currentEvent);
			final RMComponent parentCmp = new RMComponent(parentVersion, parentIp, parentTimestamp, parentPid, parentRequestId, parentEvent);
			
			// create the record from the log line
			record = new RMRecord(logFileName, recordDate, threadId, 
									currentCmp, parentCmp, 
									type, detail, 
									currentElapsed);
	
		}
		return record;
	}
	
	/**
	 * Checks if the parsing type has changed.
	 * Parsing type is considered to be changed when the last parsing type is 
	 * not unknown and if the current parsing type does not equal the last parsing type.
	 * @param currentParsingType
	 * @param lastParsingType
	 * @return true if the parsing type has changed
	 */
	private boolean parsingTypeHasChanged(String currentParsingType, String lastParsingType) {
		return !currentParsingType.equals(LogParsingTypeEvent.TYPE_UNKNOWN) && !currentParsingType.equals(lastParsingType);
	}

	private void addRmRecordToDataset(RMRecord rmRecord) {
		// add the record to a node - we always create a node
		final RMNode rmNode = new RMNode(rmRecord);
		
		// if the current record-id is the same as the parent-id, then we have a root-record
		if (rmRecord.isRootCase()) {
			// we mark the record as root record and put it in the list of root-records
			final RmRootCase rootCase = new RmRootCase(rmNode);
			rootCases.add(rootCase);
			// fill type for root case filter
			rootCaseTypes.add(rmRecord.getTypeCmp());
		
			// if we previously added a dummy record, we need to merge it with the real record
			RMNode dummyNode = allNodes.get(rmRecord.getCurrentCmp().getReqid());
			if(dummyNode != null) {
				rmNode.getChildren().addAll(dummyNode.getChildren());
			}
			allNodes.put(rmRecord.getCurrentCmp().getReqid(), rmNode);
		// otherwise the the current record is a child-record
		} 
		else {
			final Long parentNodeId = rmRecord.getParentCmp().getReqid();
			RMNode parentNode = allNodes.get(parentNodeId);
			
			// in case there is no log entry for the root record, we create a dummy record
			if(parentNode == null) {
				parentNode = new RMNode(
						new RMRecord(UNKNOWN, new Date(), UNKNOWN, 
								new RMComponent(0, UNKNOWN, 0, 0, parentNodeId, UNKNOWN), 
								new RMComponent(0, UNKNOWN, 0, 0, parentNodeId, UNKNOWN), 
								UNKNOWN, UNKNOWN + " / no root case", Long.MAX_VALUE)
				);
				allNodes.put(parentNodeId, parentNode);
			}
			parentNode.getChildren().add(rmNode);

			// If events are not in order, it can happen that we already have the current
			// nodeId in the allNodes map. Then we need to merge with the previously created dummy.
			final Long currentNodeId = rmRecord.getCurrentCmp().getReqid();
			RMNode dirtyCurrentNode = allNodes.get(currentNodeId);
			if(dirtyCurrentNode != null) {
				rmNode.getChildren().addAll(dirtyCurrentNode.getChildren());
			}
			allNodes.put(rmRecord.getCurrentCmp().getReqid(), rmNode);
		}
	}

	/**
	 * Find all nodes by the rm record id. The method looks in the parent node index.
	 *  
	 * @param nodeId the id of the rm record
	 * @return a list of RMNodes that have a reference to the supplied node id
	 * 
	 */
	@SuppressWarnings("unchecked")
	public List<RMNode> findByRmRecId(long nodeId) {
		if(LOG.isLoggable(Level.INFO)) {
			LOG.log(Level.INFO, "findByNodeId with record id " + nodeId);
		}
		final RMNode node = allNodes.get(nodeId);
		List<RMNode> result = Collections.EMPTY_LIST;
		if(node == null) {
			LOG.warning("findByRmRecId was called with an invalid node id");
		} else {
			result = allNodes.get(nodeId).getChildren();
		}
		return result;
	}
	
	/**
	 * Find all events that have no root event.
	 * @return a list of RM node that are not a root event and not referenced by a root event
	 * 
	 */
	/*
	public List<RMNode> findUnreferencedEvents() {
		LOG.info("looking for dirty events...");
		List<RMNode> result = new ArrayList<RMNode>();

		final Map<Long, Boolean> parentNodeHash = new HashMap<Long, Boolean>();
		final Map<Long, Boolean> currentNodeHash = new HashMap<Long, Boolean>();
		
		// build up a hash of all parent nodes (use the use cases for that)
		for (RmRootCase rootCase : this.rootCases) {
			parentNodeHash.put(rootCase.getRmNode().getData().getParentCmp().getReqid(), true);
		}
		
		// build up a hash of all current nodes
		for (Entry<Long, RMNode> item : this.allNodes.entrySet()) {
			List<RMNode> nodes = item.getValue().getChildren();
			for (RMNode node : nodes) {
				currentNodeHash.put(node.getData().getCurrentCmp().getReqid(), true);
			}
		}
		
		// now do some set theory and by that filter
		// TODO: we are currently destroying the parentNodeKeys which is shit
		Set<Long> parentNodeKeys = allNodes.keySet();
		parentNodeKeys.removeAll(parentNodeHash.keySet());
		parentNodeKeys.removeAll(currentNodeHash.keySet());
		
		for (Long key : parentNodeKeys) {
			rootCases.add(new DummyRmRootCase(key));
		}
		LOG.info("found " + parentNodeKeys.size() + " dirty events: " + parentNodeKeys);
		return result;
	}*/
	
	public void setElapsedTimeBorder(Long elapsedTimeBorder) {
		//TODO: remove method
	}

	public List<RmRootCase> getRootCases() {
		return rootCases;
	}
	
	public Set<String> getRootCaseTypes() {
		return rootCaseTypes;
	}

	/**
	 * resets the processor in order to be able to process a new file
	 */
	public void reset() {
		rootCases.clear();
		allNodes.clear();
		fileHandler.reset();
	}
	
	@Override
	public synchronized void addObserver(Observer o) {
		super.addObserver(o);
		fileHandler.addObserver(o);
	}
}

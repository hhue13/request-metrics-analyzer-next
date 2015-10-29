package de.ibm.issw.requestmetrics.engine.events;

import java.io.File;
import java.util.EventObject;

@SuppressWarnings("serial")
public class ParsingAllFilesHasFinishedEvent extends EventObject {
	private File file;
	
	public ParsingAllFilesHasFinishedEvent(Object source, File file) {
		super(source);
		this.file = file;
	}

	public File getFile() {
		return file;
	}
}

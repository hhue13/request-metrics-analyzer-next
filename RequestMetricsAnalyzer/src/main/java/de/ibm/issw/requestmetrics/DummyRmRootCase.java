package de.ibm.issw.requestmetrics;

import java.util.Date;

public class DummyRmRootCase extends RmRootCase {
	private static final String UNKNOWN = "unknown";
	private final Long key;

	public DummyRmRootCase(Long key) {
		super(
				new RMNode(
						new RMRecord(UNKNOWN, new Date(), UNKNOWN, 
								new RMComponent(0, UNKNOWN, 0, 0, key, UNKNOWN), 
								new RMComponent(0, UNKNOWN, 0, 0, key, UNKNOWN), 
								UNKNOWN, UNKNOWN, Long.MAX_VALUE)
				)
			);
		this.key = key;
	}

	/**
	 * renders a description string. It internally uses the data of the RM Node.
	 * @return the string of the representation of the root case.
	 */
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer()
				.append("root event not captued - request id ")
				.append(key);
		return sb.toString();
	}
}
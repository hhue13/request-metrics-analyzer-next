package de.ibm.issw.requestmetrics.gui.statistics;

import java.awt.Color;
import java.util.List;

import javax.swing.table.AbstractTableModel;

@SuppressWarnings("serial")
public class ChildNodeStatisticsTableModel extends AbstractTableModel {
	private static final String[] columnNames = {"Color", "Component", "Children", "Executions", "Total time"};
	private List<ChildNodeStatisticsEntry> entries;

	public ChildNodeStatisticsTableModel(final List<ChildNodeStatisticsEntry> entries) {
		this.entries = entries;
	}
	
	@Override
	public int getColumnCount() {
		return columnNames.length;
	}
	
	@Override
	public String getColumnName(int column) {
		return columnNames[column];
	}
	
	@Override
	public Class<?> getColumnClass(int columnIndex) {
		switch (columnIndex) {
			case 0:	return Color.class;
			case 2:	return Long.class;
			case 3:	return Long.class;
			case 4: return Long.class;
			default: return String.class;
		}
	}
	
	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		switch (columnIndex) {
			case 0:	return entries.get(rowIndex).getChartColor();
			case 2:	return entries.get(rowIndex).getNumberOfChildren();
			case 3:	return entries.get(rowIndex).getNumberOfExecutions();
			case 4: return entries.get(rowIndex).getTotalTime();
			default: return entries.get(rowIndex).getComponent();
		}
	}

	@Override
	public int getRowCount() {
		return entries.size();
	}
}

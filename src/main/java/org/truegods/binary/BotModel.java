package org.truegods.binary;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class BotModel extends AbstractTableModel
{
	public final List<Bot> bots;

	public BotModel()
	{
		this.bots = new ArrayList<Bot>(8);
	}

	public int getRowCount()
	{
		return bots.size();
	}

	public int getColumnCount()
	{
		return 4;
	}

	@Override public String getColumnName(final int column)
	{
		switch (column)
		{
			case 0:
				return "Profile";
			case 1:
				return "Server";
			case 2:
				return "CPU";
			case 3:
				return "Mem";
			default:
				throw new IllegalArgumentException();
		}
	}

	public Object getValueAt(final int row, final int column)
	{
		final Bot b = bots.get(row);
		switch (column)
		{
			case 0:
				return b.profile;
			case 1:
				return b.server;
			case 2:
				return b.running ? String.format("%.2f%%", 100.0d * b.cpu) : "";
			case 3:
				return b.running ? String.format("%.2fMB", convertTo((double) b.ram)) : "";
			default:
				throw new IllegalArgumentException();
		}
	}

	public final double convertTo(final double value)
	{
		final double convertedValue = convertNoRounding(value);
		return convertedValue - Math.floor(convertedValue) < 0.00001
		       ? (double) Math.round(convertedValue)
		       : convertedValue;
	}

	protected double convertNoRounding(final double value)
	{
		return value / 0.125 * 1.25E-7;
	}

	public void add(final Bot bot)
	{
		bots.add(bot);
		fireTableRowsInserted(bots.size() - 1, bots.size() - 1);
	}

	public void remove(final int i)
	{
		bots.remove(i);
		fireTableRowsDeleted(i, i);
	}

	public void update(final Bot bot)
	{
		fireTableCellUpdated(bots.indexOf(bot), 2);
		fireTableCellUpdated(bots.indexOf(bot), 3);
	}
}


package org.truegods.binary;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class GroupModel extends AbstractListModel implements ComboBoxModel
{
	public int selected;
	private List<String> groups;

	public GroupModel()
	{
		this.groups = new ArrayList<String>(8);
	}

	public String get(final int i)
	{
		return groups.get(i);
	}

	public int getSize()
	{
		return groups.size();
	}

	public Object getElementAt(final int i)
	{
		return groups.get(i);
	}

	public void remove(final int i)
	{
		groups.remove(i);
		selected--;
		fireIntervalRemoved(this, i, i);
	}

	public void add(final String group)
	{
		groups.add(group);
		final int end = groups.size() - 1;
		fireIntervalAdded(this, end, end);
	}

	public void setSelectedItem(final Object o)
	{
		if (o instanceof String)
			selected = groups.indexOf(o);
	}

	public Object getSelectedItem()
	{
		return groups.get(selected);
	}

	public boolean contains(final String group)
	{
		return groups.contains(group);
	}
}

package org.truegods.binary;

import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;
import org.hyperic.sigar.ptql.ProcessQueryFactory;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.prefs.Preferences;

import static java.lang.System.*;
import static java.util.concurrent.TimeUnit.*;
import static javax.swing.JFileChooser.*;
import static javax.swing.JOptionPane.*;

public class BinaryManager extends JFrame
{
	private JPanel contentPane;
	private JTable bots;
	private JTextField profile;
	private JButton add;
	private JTextField server;
	private JButton stop;
	private JButton start;
	private JButton restart;
	private JButton delete;
	private JLabel yaeb;
	private JButton change;
	private JCheckBox auto;
	private JComboBox group;
	private JButton addGroup;
	private JButton deleteGroup;
	private List<BotModel> models;
	private final Sigar sigar;
	private File temp;
	private final GroupModel groups;
	private int selectedGroup;

	private final Lock lock = new ReentrantLock();

	private void selectGroup(final int group)
	{
		lock.lock();
		try
		{
			selectedGroup = group;
			bots.setModel(models.get(group));
		}
		finally
		{
			lock.unlock();
		}
	}

	public BinaryManager()
	{
		setContentPane(contentPane);
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		models = new ArrayList<BotModel>(1);
		groups = new GroupModel();
		group.addActionListener(new ActionListener()
		{
			public void actionPerformed(final ActionEvent event)
			{
				selectedGroup = group.getSelectedIndex();
				selectGroup(group.getSelectedIndex());
			}
		});

		addGroup.addActionListener(new ActionListener()
		{
			public void actionPerformed(final ActionEvent event)
			{
				onAddGroup();
			}
		});
		deleteGroup.addActionListener(new ActionListener()
		{
			public void actionPerformed(final ActionEvent event)
			{
				onDeleteGroup();
			}
		});

		bots.getSelectionModel().addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(final ListSelectionEvent event)
			{
				enableButtons();
			}
		});

		add.addActionListener(new ActionListener()
		{
			public void actionPerformed(final ActionEvent event)
			{
				onAdd();
			}
		});
		delete.addActionListener(new ActionListener()
		{
			public void actionPerformed(final ActionEvent event)
			{
				onDelete();
			}
		});
		change.addActionListener(new ActionListener()
		{
			public void actionPerformed(final ActionEvent event)
			{
				onChange();
			}
		});
		start.addActionListener(new ActionListener()
		{
			public void actionPerformed(final ActionEvent event)
			{
				onStart();
			}
		});
		stop.addActionListener(new ActionListener()
		{
			public void actionPerformed(final ActionEvent event)
			{
				onStop();
			}
		});
		restart.addActionListener(new ActionListener()
		{
			public void actionPerformed(final ActionEvent event)
			{
				onRestart();
			}
		});

		sigar = new Sigar();
		try
		{
			temp = File.createTempFile("binary", "tmp");
			temp = new File(temp.getParentFile(), String.format("%d.yaeb", System.currentTimeMillis()));
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static void copyFolder(final File src, final File dest)
			throws IOException, InterruptedException
	{
		final ProcessBuilder pb = new ProcessBuilder("cp", "-r", src.getAbsolutePath(), dest.getAbsolutePath());
		final Process p = pb.start();
		p.waitFor();

	}

	private void enableButtons()
	{
		final int[] rows = bots.getSelectedRows();
		boolean hasRunning = false;
		boolean hasNotRunning = false;
		final BotModel model = models.get(selectedGroup);
		for (final int row : rows)
		{
			final Bot bot = model.bots.get(row);
			hasRunning |= bot.running;
			hasNotRunning |= !bot.running;
		}
		start.setEnabled(hasNotRunning);
		restart.setEnabled(hasRunning);
		stop.setEnabled(hasRunning);
		delete.setEnabled(rows.length > 0);
	}

	public static void main(final String[] args)
	{
		final BinaryManager dialog = new BinaryManager();
		dialog.pack();
		dialog.setSize(640, 350);
		dialog.init();
		dialog.setLocationRelativeTo(null);
		dialog.setVisible(true);
	}

	private void init()
	{
		loadConfig();
		auto.addChangeListener(new ChangeListener()
		{
			public void stateChanged(final ChangeEvent event)
			{
				saveConfig();
			}
		});
		out.println("querying sigar");
		Cron.interval(5, SECONDS, new CronJob()
		{
			public String getName()
			{
				return "Sigar Update";
			}

			public void exec(final CronStatus status) throws Throwable
			{
				lock.lock();
				try
				{
					final long[] pids = ProcessQueryFactory.getInstance().getQuery("Exe.Name.ew=mdm_flash_player").find(sigar);
					final Set<Long> running = new HashSet<Long>(pids.length);
					final BotModel model = models.get(selectedGroup);
					for (final long pid : pids)
					{
						running.add(pid);
						final String[] args = sigar.getProcArgs(pid);
						final double cpu = sigar.getProcCpu(pid).getPercent();
						final long mem = sigar.getProcMem(pid).getResident();
						boolean foundProfile = false;
						boolean foundServer = false;
						String profile = "";
						String server = null;

						for (final String arg : args)
						{
							if (foundProfile)
							{
								profile = arg;
								foundProfile = false;
							}
							else if (foundServer)
							{
								server = arg;
								foundServer = false;
							}
							else if ("-profile".equals(arg))
								foundProfile = true;
							else if ("-server".equals(arg))
								foundServer = true;

						}
						if (profile != null && server != null)
						{
							for (final Bot bot : model.bots)
							{
								if (server.equals(bot.server) && profile.equals(bot.profile))
								{
									bot.cpu = cpu;
									bot.ram = mem;
									bot.running = true;
									bot.pid = pid;
									model.update(bot);
									break;
								}
							}
						}
					}
					final Set<Bot> closed = new HashSet<Bot>(0);
					final Set<Bot> hogs = new HashSet<Bot>(0);
					for (final Bot bot : model.bots)
					{
						if (bot.running && !running.contains(bot.pid))
						{
							bot.running = false;
							bot.pid = 0;
							bot.cpu = null;
							bot.ram = null;
							model.update(bot);
							closed.add(bot);
						}
						if(bot.running && bot.ram > 314572800L)
							hogs.add(bot);
					}
					if (auto.isSelected())
					{
						int delay = 0;
						for (final Bot bot : closed)
						{
							start(delay, bot);
							delay += 1;
						}
						for (final Bot hog : hogs)
							kill(hog);
						for (final Bot hog : hogs)
						{
							start(delay,hog);
							delay+=1;
						}
					}

				}
				finally
				{
					lock.unlock();
				}
			}
		});
	}

	private void loadConfig()
	{
		final Preferences preferences = Preferences.userNodeForPackage(BinaryManager.class);
		yaeb.setText(preferences.get("yaeb", "none"));
		auto.setSelected(preferences.getBoolean("auto", true));
		final int numGroups = preferences.getInt("numGroups", 1);

		for (int i = 0; i < numGroups; i++)
		{
			groups.add(i == 0 ? "Default" : preferences.get("group" + i, ""));
			loadGroup(i);
		}

		selectGroup(0);
		group.setModel(groups);
	}

	private String prefix(final int group, final String key)
	{
		return group == 0 ? key : String.format("%s%s", groups.get(group), key);
	}

	private void loadGroup(final int group)
	{
		final Preferences preferences = Preferences.userNodeForPackage(BinaryManager.class);

		final int numBots = preferences.getInt(prefix(group, "numBots"), 0);
		final BotModel model = new BotModel();
		for (int i = 0; i < numBots; i++)
		{
			final String profile = preferences.get(prefix(group, "bot" + i + ".profile"), "");
			final String server = preferences.get(prefix(group, "bot" + i + ".server"), "");
			model.add(new Bot(profile, server));
		}
		model.addTableModelListener(new TableModelListener()
		{
			public void tableChanged(final TableModelEvent event)
			{
				enableButtons();
			}
		});
		models.add(model);

	}

	private void saveConfig()
	{
		final Preferences preferences = Preferences.userNodeForPackage(BinaryManager.class);
		preferences.put("yaeb", yaeb.getText());
		preferences.putBoolean("auto", auto.isSelected());
		final int size = groups.getSize();
		preferences.putInt("numGroups", size);
		for (int i = 0; i < size; i++)
		{
			preferences.put("group" + i, groups.get(i));
			saveGroup(i);
		}
	}

	private void saveGroup(final int group)
	{
		final Preferences preferences = Preferences.userNodeForPackage(BinaryManager.class);
		final BotModel model = models.get(group);
		preferences.putInt(prefix(group, "numBots"), model.bots.size());
		int i = 0;
		for (final Bot bot : model.bots)
		{
			preferences.put(prefix(group, "bot" + i + ".profile"), bot.profile);
			preferences.put(prefix(group, "bot" + i + ".server"), bot.server);
			i++;
		}

	}

	private void onAdd()
	{
		final BotModel model = models.get(selectedGroup);
		model.add(new Bot(profile.getText(), server.getText()));
		saveConfig();
	}

	private void onDelete()
	{
		final BotModel model = models.get(selectedGroup);
		final int[] rows = bots.getSelectedRows();
		for (int i = rows.length - 1; i >= 0; i--)
			model.remove(rows[i]);
		saveConfig();
	}

	private void onChange()
	{
		final JFileChooser chooser = new JFileChooser();
		final FileNameExtensionFilter filter = new FileNameExtensionFilter("Applications", "exe", "app");
		chooser.setFileFilter(filter);
		if (chooser.showOpenDialog(this) == APPROVE_OPTION)
		{
			yaeb.setText(chooser.getSelectedFile().getAbsolutePath());
			saveConfig();
			if (temp.exists())
				temp.delete();
		}
	}

	private void onStart()
	{
		final BotModel model = models.get(selectedGroup);
		int delay = 0;
		for (final int row : bots.getSelectedRows())
		{
			final Bot bot = model.bots.get(row);
			if (!bot.running)
			{
				start(delay, bot);
				delay += 1;
			}
		}
	}

	private void start(int delay, final Bot bot)
	{
		final File yaeb = new File(this.yaeb.getText());
		if (!yaeb.exists())
		{
			showMessageDialog(this, String.format("%s is not present", yaeb.getAbsolutePath()));
			return;
		}
		final ProcessBuilder builder = new ProcessBuilder();
		builder.directory(temp.getParentFile());
		final File profileApp = new File(temp.getParentFile(), String.format("%s %s.app", bot.profile, bot.server));
		if (!temp.exists())
		{
			try
			{
				copyFolder(yaeb, temp);
			}
			catch (IOException e)
			{
				throw new RuntimeException(e);
			}
			catch (InterruptedException e)
			{
				throw new RuntimeException(e);
			}
		}
		if (temp.renameTo(profileApp))
		{
			builder.command("open", "-n", profileApp.getName(), "--args", "-autostart", "-autorun", "-profile", bot.profile,
			                "-server", bot.server,
			                "-delay", Integer.toString(delay));
		}
		else
		{
			System.err.printf("could not rename %s to %s%n",temp.getAbsolutePath(),profileApp.getAbsolutePath());
			return;
		}
		try
		{
			final Process proc = builder.start();
			proc.waitFor();
			Thread.sleep(10000);
			if (!profileApp.renameTo(temp))
				System.err.println("couldn't name back to original");
			delay += 1;
		}
		catch (IOException e)
		{
			showMessageDialog(this, e.getMessage());
		}
		catch (InterruptedException e)
		{
			showMessageDialog(this, e.getMessage());
		}
	}

	private void onStop()
	{
		final BotModel model = models.get(selectedGroup);
		for (final int row : bots.getSelectedRows())
			kill(model.bots.get(row));
	}

	private void kill(final Bot bot)
	{
		final BotModel model = models.get(selectedGroup);
		try
		{
			sigar.kill(bot.pid, 1);
			bot.pid = 0;
			bot.running = false;
			bot.cpu = null;
			bot.ram = null;
			model.update(bot);
		}
		catch (SigarException e)
		{
			showMessageDialog(this, e.getMessage());
		}
	}

	private void onRestart()
	{
		final BotModel model = models.get(selectedGroup);
		final Set<Bot> toRestart = new HashSet<Bot>(0);
		for (final int row : bots.getSelectedRows())
		{
			final Bot bot = model.bots.get(row);
			if (bot.running)
			{
				toRestart.add(bot);
				kill(bot);
			}
		}
		int delay = 0;
		for (final Bot bot : toRestart)
		{
			start(delay, bot);
			delay += 1;
		}
	}

	private void onAddGroup()
	{
		final String newGroup = showInputDialog(this, "Name", "Add Group", QUESTION_MESSAGE);
		if (newGroup != null && !newGroup.equals(""))
		{
			if (groups.contains(newGroup))
			{
				showMessageDialog(this, String.format("Group %s already exists", newGroup));
			}
			else
			{
				groups.add(newGroup);
				models.add(new BotModel());
				saveConfig();
				this.group.setSelectedIndex(groups.getSize()-1);
			}
		}
	}

	private void onDeleteGroup()
	{
		if (selectedGroup == 0)
			showMessageDialog(this, String.format("You can not remove the default group"));
		else
		{
			switch (showConfirmDialog(this, String.format("Delete Group %s?", groups.get(selectedGroup)), "Delete Group",
			                          YES_NO_OPTION))
			{
				case YES_OPTION:
					models.remove(selectedGroup);
					groups.remove(selectedGroup);
					saveConfig();
					this.group.setSelectedIndex(selectedGroup);
					break;
			}
		}

	}

}

package us.bringardner.shell;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.xml.crypto.Data;

import us.bringardner.shell.Console.HistoryEntry;

public class HistorySearchDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private final JPanel contentPanel = new JPanel();
	private JTextField filterTextField;
	private JTable table;
	private Console console;
	private List<HistoryEntry> filterd = new ArrayList<>();
	
	protected boolean canceled;
	
	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		try {
			HistorySearchDialog dialog = new HistorySearchDialog();
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			
			Console console = new Console();
			console.readHistory();
			String cmd = dialog.showDialog(console);
			System.out.println(cmd);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create the dialog.
	 */
	public HistorySearchDialog() {
		setBounds(100, 100, 450, 300);
		getContentPane().setLayout(new BorderLayout());
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(contentPanel, BorderLayout.CENTER);
		contentPanel.setLayout(new BorderLayout(0, 0));
		{
			JScrollPane scrollPane = new JScrollPane();
			contentPanel.add(scrollPane);
			{
				table = new JTable();
				scrollPane.setViewportView(table);
			}
		}
		{
			JPanel buttonPane = new JPanel();
			buttonPane.setLayout(new FlowLayout(FlowLayout.CENTER));
			getContentPane().add(buttonPane, BorderLayout.SOUTH);
			{
				JButton okButton = new JButton("OK");
				okButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						dispose();
					}
				});
				okButton.setActionCommand("OK");
				buttonPane.add(okButton);
				getRootPane().setDefaultButton(okButton);
			}
			{
				JButton cancelButton = new JButton("Cancel");
				cancelButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						canceled = true;
						dispose();
					}
				});
				cancelButton.setActionCommand("Cancel");
				buttonPane.add(cancelButton);
			}
		}
		{
			JPanel panel = new JPanel();
			getContentPane().add(panel, BorderLayout.NORTH);
			{
				JLabel lblNewLabel = new JLabel("Filter");
				panel.add(lblNewLabel);
			}
			{
				filterTextField = new JTextField();
				filterTextField.addKeyListener(new KeyAdapter() {
					@Override
					public void keyTyped(KeyEvent e) {
						SwingUtilities.invokeLater(()->actionFilter());
					}
				});
				panel.add(filterTextField);
				filterTextField.setColumns(20);
			}
		}
	}

	protected void actionFilter() {
		String filter = filterTextField.getText();
		if( !filter.isEmpty()) {
			List<HistoryEntry> tmp = new ArrayList<>();
			for(int idx=0, sz=console.history.size(); idx < sz; idx++) {
				HistoryEntry he = console.history.get(idx);
				if( he.command.contains(filter)) {
					tmp.add(he);
				}
			}
			filterd = tmp;
		
		} else {
			filterd = console.history;
		}
		((DefaultTableModel)table.getModel()).fireTableDataChanged();
		
	}

	public String showDialog(Console console) {
		String ret = null;
		this.console = console;
		filterd = console.history;
		
		table.setModel(new DefaultTableModel() {
			private static final long serialVersionUID = 1L;

			@Override
			public int getColumnCount() {
				return 2;
			}
			
			@Override
			public String getColumnName(int column) {
				if( column==0) {
					return "time";
				} 
				return "Command";
			}
			
			@Override
			public Class<?> getColumnClass(int columnIndex) {
				if( columnIndex==0) {
					return Data.class;
				} 
				return String.class;
			}
			@Override
			public int getRowCount() {
				return filterd.size();
			}
			
			@Override
			public Object getValueAt(int row, int column) {
				HistoryEntry h = filterd.get(row);
				if( column==0) {
					return new Date(h.time);
				}
				return h.command;
			}
			
		});
		setModal(true);
		setVisible(true);
		
		if( !canceled ) {
			if( table.getSelectedRow()>=0) {
				ret = filterd.get(table.getSelectedRow()).command;
			}
		}
		
		return ret;
	}
}

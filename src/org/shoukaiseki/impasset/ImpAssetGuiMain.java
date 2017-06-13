package org.shoukaiseki.impasset;




/**
 * 1.树结构 LOCHIERARCHY的SYSTEMID来自LOCSYSTEM的SYSTEMID
 * 2.asset.asset=LOCHIERARCHY.asset,显示的名称为asset.DESCRIPTION
配置文件为运行目录下的ImpKksGui.ini文件

1.点击创建表格来创建KKS导入時用的表,表名默认为shoukaiseki_insert_kks
	创建完成后将EXCEL中的KKS数据导入该表中



2.将多个EXCEL文件(下文称为子EXCEL)存放至一个EXCEL(下文称为总EXCEL)文件中
主要字段描述:
ID				在EXCEL总表导入時给每位编上EXCEL中的行号
PARENT  		个人认为应该称为"依赖","索引",详见"依赖关系"按钮
CFL 			重复LOCATION值的标识,详见下面"重复编码"按钮使用
CFD
ALLCFL
ALLCFD	
SETUMEI			备用字段,个人习惯为存放"子EXCEL"名
SETUMEINUM		备用字段,个人习惯用于存放"子EXCEL"的行号
SETUME2			存放与asset表location字段重复的行的DESCRIPTION值,如果description值相同则此值为asset表中的location字段值


3.按钮説明
创建表格  		创建KKS导入時用的表,表名默认为shoukaiseki_insert_kks
删除表格		删除shoukaiseki_insert_kks表
删除空行		删除shoukaiseki_insert_kks中的空行

清首尾空		清除shoukaiseki_insert_kks表的location和description字段首尾空
重复编码		先查询shoukaiseki_insert_kks表,对location重复的行进行标识,CFL为标识位,相同的为同一编号,编号从10000开始,如果description也相同则设置CFD与CFL同值.  再查询shoukaiseki_insert_kks表的location字段与asset的location字段,ALLCFL字段为标识位,两表之间的location字段一样时对ALLCFL值进行标识,如果description值相同则将ALLCFD也进行标识.
依赖关系		即location值之间的子父级关系,形成树结构的关键,事先了解树结构.
LOCSYSTEM表的SYSTEMID为系统名称,该名默认值为"PRIMARY",下面就已SYSTEMID为"PRIMARY"为基准,显示该系统下的最高索引符合条件为:
①LOCHIERARCHY.SYSTEMID='PRIMARY'
②LOCHIERARCHY.PARENT is null
③EXISTS(SELECT a.description FROM asset a WHERE a.location=c.location)
参考如下(最高索引只显示一条,其它SITEID等字段请按实际插入,与LOCHIERARCHY.CHILDREN字段无关,调试系统为MAXIMO7.2,仅供参考,事实为准)
select distinct C.location,(select a.DESCRIPTION from asset a where a.location=C.location )
from asset a, LOCSYSTEM B,LOCHIERARCHY C where C.SYSTEMID='PRIMARY' and C.parent                                                              is null
AND exists (SELECT a.description FROM asset a WHERE a.location=c.location) and rownum=1
最高索引(假如为 ASUS)的子索引为SELECT * FROM LOCHIERARCHY WHERE LOCHIERARCHY.PARENT='ASUS' AND SYSTEMID='PRIMARY',依次类推
所以每一个索引直接都存在依赖问题,该方法的详细流程请查看流程图.

全库依赖			对parent值为空的通过查询asset表进行依赖处理
********************************************
**注:只对parent值为空的进行索引
********************************************
删除索引			清空shoukaiseki_insert_kks.parent字段
插入数据			将shoukaiseki_insert_kks的数据插入五张表中
删一重复			删根据标识位删除其中一条重复的,用于测试,插入正式表シ勿用

*/

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.text.BadLocationException;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.util.JMXUtils;
import com.shoukaiseki.characterdetector.CharacterEncoding;
import com.shoukaiseki.characterdetector.utf.UnicodeReader;
import com.shoukaiseki.constantlib.CharacterEncodingName;
import com.shoukaiseki.gui.flowlayout.ModifiedFlowLayout;
import com.shoukaiseki.gui.jtextpane.JTextPaneDoc;
import com.shoukaiseki.sql.ConnectionKonnfigu;
import com.shoukaiseki.sql.oracle.OracleSqlDetabese;


/** org.shoukaiseki.impasset.ImpAssetGuiMain
 * @author 蒋カイセキ    Japan-Tokyo  2017年6月13日
 * ブログ http://shoukaiseki.blog.163.com/
 * E-メール jiang28555@Gmail.com
 */
public class ImpAssetGuiMain extends JFrame implements ScrollPaneConstants {

	int assetnumSeqDigits=4;
	
	String systemid;

	/**
	 * 关闭時是否退出程序,默认時退出,只作为子程序时不退出
	 */
	private boolean windowclose = true;
	public static String datetime="sysdate";//
//	public static String datetime="TO_DATE( '2011/11/27 16:16:16', 'YYYY-MM-DD HH24:MI:SS')";//

	
	
	public static String type ="操作";		//"OPERATING"		//跟显示有关
	public static String status ="操作中";	//"OPERATING"//跟显示有关

	private int parentLength = 5;// barent最低位数,用于自动解决依赖问题的查找位数,小于此则取父值首字母
	private int parentCutLength = 2;// Location首次截取的位数后成为barent
	private int parentLowerLength = 2;// barent查询Location字段時的最小长度

	private String sql = ""; 
//	private ConCatLineBreaksVector savesql=new ConCatLineBreaksVector();
	
	private String fileName = "./ImpAssetGui.ini";
	public File txtfile = new File(fileName);
	private String sqlName = "./ImpAssetGui.sql";
	public File sqlNameFile = new File(sqlName);
	private String shoukaiseki_insert_asset = "temp001";// 新增KKS编码存放至该表shoukaiseki_insert_kks

	private SimpleDateFormat bartDateFormat = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss");
	private String asset = "asset";
	private String[] parent;
	private String parentOne;

	private Box box;
	private static JTextPaneDoc textPane;
	private JLabel jl;
	private JButton commitButton,rollbackButton ;
	private JButton jb_hyou, jb_delnull,// 新建表按钮,删除空数据;
			jb_kurikaesu, jb_yilai, jb_insert, jb_delkurikaesu,jb_insertteam,jb_updatetree; // 重复编号按钮;依赖按钮;插入数据按钮,删除重复的其中一条

	private JButton jb_delhyou, jb_ldtrim, jb_delyilai, jb_allyilai;// 删除表,清首位空,删除依赖,全库依赖

	private JTextField jl_parent;
	// private JTextField jt_loginurl,jt_loginuser,jt_loginpassword;

	//
	private DecimalFormat df = new DecimalFormat("00.00%"); // " "内写格式的模式
	// 如保留2位就用"0.00"即可
	private int error = 0;// 记录数据更新失败记录
	private int endtj = 0;// 统计总更新数据条数
	private boolean conCOR=false;//是否有提交数据状态

	OracleSqlDetabese osd=null;
	private PreparedStatement pst ;
	private Connection con = null;
	private Statement sm = null;
	private ResultSet rs = null;
	private String tableName = null;
	private String cName = null;
	private String result = null;
	private String url = "jdbc:oracle:thin:@127.0.0.1:1521:orcl";
	// private String url = "jdbc:oracle:thin:@192.168.2.101:1521:zhjqmaximo";
	private String driver = "oracle.jdbc.driver.OracleDriver";
	private String user = "orclzhjq";
	private String password = "orclzhjq";
	private PreparedStatement psSelectParent;
	private PreparedStatement psSelectLP;
	private PreparedStatement psSelectId;
	
	
	private PreparedStatement findassetPS;
	private PreparedStatement findassettempPS;
	private ResultSet findassetRS;

	private String allsiteid;

	private String lochierarchy;

	public ImpAssetGuiMain() throws BadLocationException {
		this(true);
	}


	/**
	 * 
	 */
	public ImpAssetGuiMain(boolean windowclose) throws BadLocationException {

		textPane = new JTextPaneDoc();
		textPane.setCursor(new Cursor(Cursor.TEXT_CURSOR));
		textPane.setText("这里设置文本框内容!");
		textPane.setFont(new Font("宋体", Font.BOLD, 13));
		textPane.setLayout(new ModifiedFlowLayout());// 加不加都感觉有效果,如果一段英文无空格就会出现不会自动换行

		jb_hyou = new JButton("创建表格");
		jb_delnull = new JButton("删除空行");
		jb_kurikaesu = new JButton("处理重复编码");
		jb_yilai = new JButton("依赖关系");
		
		jb_insert = new JButton("写入编码");
		jb_insertteam = new JButton("分組写入编码");
		jb_updatetree = new JButton("更新树结构");
		commitButton=new JButton("提交更改");
		
		
		//隐藏按钮
		jb_insert.setVisible(false);
		jb_insertteam.setVisible(false);
		jb_updatetree.setVisible(false);
		jb_yilai.setVisible(false);
		
		jb_hyou.addActionListener(new ActionListener() { // 插入文字的事件
					public void actionPerformed(ActionEvent e) {

						hyou();
					}
				});
		jb_delnull.addActionListener(new ActionListener() { // 插入文字的事件
					public void actionPerformed(ActionEvent e) {
						sql = "delete from "
								+ shoukaiseki_insert_asset
								+ " where  trim(asset) is null AND  trim(description) is null";
						textPane
								.addLastLine("准备删除asset字段和description字段为空的行!");
						println(sql);
						if (update(con, sql)) {
							commitButton.setEnabled(true);
							rollbackButton.setEnabled(true);
							printlnSeikou();
						} else {
							commitButton.setEnabled(false);
							rollbackButton.setEnabled(false);
							printlnSippai();
						}

					}
				});
		jb_kurikaesu.addActionListener(new ActionListener() { // 插入文字的事件
					public void actionPerformed(ActionEvent e) {
						int suteetasu = JOptionPane.showConfirmDialog(null,
								"确定更新该表重复编码错误字段吗?", "提示!!",
								JOptionPane.YES_NO_OPTION);
						if (suteetasu == 0) {
							new Timer().schedule(new TimerTask() {
								@Override
								public void run() {// 实例中的的方法
									kuRiKaeSu();// 定时器到后执行的方法,一般在定时器到后的内容具体在外面写
									commitButton.setEnabled(true);
									rollbackButton.setEnabled(true);
								}
							}, 10);
							println(shoukaiseki_insert_asset
									+ "更新重复编码错误字段完成!");
						} else {
							println(shoukaiseki_insert_asset
									+ "已取消操作!");
						}
					}
				});

		jb_yilai.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new Timer().schedule(new TimerTask() {
					@Override
					public void run() {// 实例中的的方法
						yilai();
						commitButton.setEnabled(true);
						rollbackButton.setEnabled(true);
					}
				}, 10);
			}
		});

		jb_insert.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new Timer().schedule(new TimerTask() {
					@Override
					public void run() {// 实例中的的方法
						try {
							if ( selectParentHaveNull()) {
								wrFileSql();
							}
						} catch (BadLocationException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}, 10);
			}
		});
		jb_insertteam.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new Timer().schedule(new TimerTask() {
					@Override
					public void run() {// 实例中的的方法
						try {
							if (  selectParentHaveNull()) {
//								savesql.setContent("");
								OracleSqlDetabese osd=new OracleSqlDetabese(con);
								osd.setSql(new StringBuffer("select SETUMEINUM from ").append(shoukaiseki_insert_asset).append(" where setumeinum is not null group by SETUMEINUM").toString());
								ResultSet r = osd.executeQuery();
								 while(r.next()) {
									systemid=r.getString(1);
									wrFileSql();
								}
								 osd.close();
							}
						} catch (BadLocationException | SQLException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}, 10);
			}
		});
		jb_updatetree.addActionListener(new ActionListener() {// 添加事件
			public void actionPerformed(ActionEvent e) {
				new Timer().schedule(new TimerTask() {
					@Override
					public void run() {// 实例中的的方法
						try {
							printlnSeikou();
							commitButton.setEnabled(true);
							rollbackButton.setEnabled(true);
						} catch (Exception e1) {
							// TODO Auto-generated catch block
							conRollback();
							e1.printStackTrace();
							println(e1.getMessage(),true);
							printlnSippai();
						}
					}
				}, 10);
			}

		});

		 commitButton.addActionListener(new ActionListener() {// 添加事件
					public void actionPerformed(ActionEvent e) {
						conCommit();
					}
				});
		
		jb_delhyou = new JButton("删除表格");
		jb_ldtrim = new JButton("清首尾空");
		jb_delyilai = new JButton("删除依赖");
		jb_allyilai = new JButton("全库依赖");
		jb_delkurikaesu = new JButton("删一重复");
		rollbackButton = new JButton("回退提交"); 
		
		//隐藏按钮
		jb_delhyou.setVisible(false);
		jb_delyilai.setVisible(false);
		jb_allyilai.setVisible(false);
		jb_delkurikaesu.setVisible(false);
		
		jb_delhyou.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				delHyou();
			}
		});
		jb_ldtrim.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new Timer().schedule(new TimerTask() {
					@Override
					public void run() {// 实例中的的方法
						try {
							delLDTrim();
						} catch (BadLocationException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}// 请Location,Description
					}
				}, 10);
			}
		});
		jb_delyilai.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

				int suteetasu = JOptionPane
						.showConfirmDialog(null, "确定清空依赖所在parent字段吗?", "提示!!",
								JOptionPane.YES_NO_OPTION);
				if (suteetasu == 0) {
					new Timer().schedule(new TimerTask() {
						@Override
						public void run() {// 实例中的的方法
							// 清空Parent字段
							sql = "UPDATE " + shoukaiseki_insert_asset
									+ " set PARENT =null";
							println("准备清空依赖所在parent字段!");
							println(sql);
							if (update(con, sql)) {
								printlnSeikou();
							} else {
								printlnSippai();
							}
							commitButton.setEnabled(true);
							rollbackButton.setEnabled(true);
						}
					}, 10);
				} else {
					println("已取消操作");
				}

			}
		});

		jb_allyilai.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new Timer().schedule(new TimerTask() {
					@Override
					public void run() {// 实例中的的方法
						allYilai();// 查找asset表的依赖值
						commitButton.setEnabled(true);
						rollbackButton.setEnabled(true);
					}
				}, 10);
			}
		});
		jb_delkurikaesu.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new Timer().schedule(new TimerTask() {
					@Override
					public void run() {// 实例中的的方法
						int suteetasu = JOptionPane.showConfirmDialog(null,
								"确定删除重复编码的其中一条吗?", "提示!!",
								JOptionPane.YES_NO_OPTION);
						if (suteetasu == 0) {
							new Timer().schedule(new TimerTask() {
								@Override
								public void run() {// 实例中的的方法
									sql = "select 'delete from "
											+ shoukaiseki_insert_asset
											+ " where id='||id||';' from (select a.*, ROW_NUMBER() "
											+ " over(partition by CFL　ORDER by CFL)　RN from "
											+ shoukaiseki_insert_asset
											+ " a where cfl<>0) where RN=1 ";
									println("准备清空依赖所在parent字段!");
									println(sql);
									if (update(con, sql)) {
										printlnSeikou();
									} else {
										printlnSippai();
									}
								}
							}, 10);
						} else {
							println("已取消操作");
						}
					}
				}, 10);
			}
		});
		
		rollbackButton.addActionListener(new ActionListener() {// 添加事件
				public void actionPerformed(ActionEvent e) {
						conRollback();
				}
			});


		// 设置主框架的布局
		Container c = getContentPane();
		// c.setLayout(new BorderLayout());

		JScrollPane jsp = new JScrollPane(textPane);// 新建一个滚动条界面，将文本框传入
		// 滚动条
		jsp.setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_AS_NEEDED);

		jl = new JLabel("状态无异常!", SwingConstants.LEFT);
		JLabel jl1 = new JLabel(
				"java -version "
						+ System.getProperty("java.version")
						+ "        Chiang Kai-shek(shoukaiseki) <jiang28555@gmail.com>  "
						+ "  2012-02-20 Tokyo japan ");
		// jl1.setBorder(BorderFactory.createEtchedBorder());//分界线
		jl1.setFont(new Font("标楷体 ", Font.BOLD, 13));
		Box box = Box.createVerticalBox(); // 竖结构
		Box box1 = Box.createHorizontalBox(); // 横结构
		Box box10 = Box.createHorizontalBox(); // 横结构
		Box box11 = Box.createHorizontalBox(); // 横结构
		Box boxfrom = Box.createHorizontalBox(); // 横结构
		Box boxto = Box.createHorizontalBox(); // 横结构
		Box boxparent = Box.createHorizontalBox(); // 横结构

		JPanel jpfrom = new JPanel();
		jpfrom.add(boxfrom);
		JPanel jpto = new JPanel();
		jpto.add(boxto);

		box11.add(jl1);

		box.add(box11);
		box.add(Box.createVerticalStrut(8)); // 两行的间距
		box.add(box1);
		box.add(Box.createVerticalStrut(8)); // 两行的间距
		box.add(box10);
		box.add(Box.createVerticalStrut(8)); // 两行的间距
		box.add(jpfrom);
		box.add(Box.createVerticalStrut(8)); // 两行的间距
		box.add(jpto);
		box.add(Box.createVerticalStrut(8)); // 两行的间距
		box.add(boxparent);

		box.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8)); // 8个的边距

//		box1.add(jb_hyou);
//		box1.add(Box.createHorizontalStrut(8));// 间距
		box1.add(jb_delnull);
		box1.add(Box.createHorizontalStrut(8));// 间距
		box1.add(jb_kurikaesu);
		box1.add(Box.createHorizontalStrut(8));// 间距
		box1.add(jb_yilai);
		box1.add(Box.createHorizontalStrut(8));// 间距
		box1.add(jb_insert);
		box1.add(Box.createHorizontalStrut(8));// 间距
		box1.add(jb_insertteam);
		box1.add(Box.createHorizontalStrut(8));// 间距
		box1.add(jb_updatetree);
		box1.add(Box.createHorizontalStrut(8));// 间距
		box1.add(commitButton);

		box10.add(jb_delhyou);
		box10.add(Box.createHorizontalStrut(8));// 间距
		box10.add(jb_ldtrim);
		box10.add(Box.createHorizontalStrut(8));// 间距
		box10.add(jb_delyilai);
		box10.add(Box.createHorizontalStrut(8));// 间距
		box10.add(jb_allyilai);
		box10.add(Box.createHorizontalStrut(8));// 间距
		box10.add(jb_delkurikaesu);
		box10.add(Box.createHorizontalStrut(8));// 间距
		box10.add(rollbackButton);



		jl_parent = new JTextField("状态:");
		jl_parent.setEditable(false);


		box.add(Box.createVerticalStrut(8));
		box.add(jl_parent);

		Box box2 = Box.createHorizontalBox(); // 横结构
		box2.add(new JLabel("状态栏:", SwingConstants.LEFT));
		box2.add(Box.createHorizontalStrut(8));// 间距
		box2.add(jl);

		jsp.setBorder(BorderFactory.createEtchedBorder());// 分界线

		c.add(box, BorderLayout.NORTH);
		c.add(jsp, BorderLayout.CENTER);
		c.add(box2, BorderLayout.SOUTH);

		setSize(700, 500);
		// 隐藏frame的标题栏,此功暂时关闭，以方便使用window事件
		setLocation(200, 150);

		setTitle("Kks导入程序");
		show();
		textPane.cleanText();
		this.windowclose=windowclose;

		if (windowclose) {
			this.setDefaultCloseOperation(EXIT_ON_CLOSE);
		}

		commitButton.setEnabled(false);
		rollbackButton.setEnabled(false);
	}

	/**
	 * @param args
	 * @throws IOException
	 * @throws BadLocationException
	 */
	public static void main(String[] args) throws IOException,
			BadLocationException {
		ImpAssetGuiMain kks = new ImpAssetGuiMain();
		kks.readSetting();
		// TODO Auto-generated method stub
		BufferedReader input = new BufferedReader(new InputStreamReader(
				System.in));
		kks.setDefuorutoConnection();
//		kks.println("已经开启五张表插入!2012年3月31日11时22分15秒",true);
		kks.println("已经开启设置插入!2017年6月13日13时01分43秒",true);
	}
	public void setConnection(ConnectionKonnfigu ck) throws BadLocationException{
		try {
			 this.url=ck.getUrl();
			 this.driver=ck.getDriver();
			 this.user=ck.getUser();
			 this.password=ck.getPassword();
			// （1）装载并注册数据库的JDBC驱动程序
			// 载入JDBC驱动：oracle安装目录下的jdbc\lib\classes12.jar
			println("正在试图加载驱动程序 " + driver);
			Class.forName(driver);
			println("驱动程序已加载");
			// 注册JDBC驱动：有些地方可不用此句
			println("url=" + url);
			println("user=" + user);
			println("password=" + password);
			println("正在试图连接数据库--------");
			java.sql.DriverManager
					.registerDriver(new oracle.jdbc.driver.OracleDriver());

			con = DriverManager.getConnection(url, user, password);
			println("OK,成功连接到数据库");

			/**
			 * 关闭自动更新
			 */
			con.setAutoCommit(false);
			printConnection();
		} catch (Exception ex) {
			ex.printStackTrace();
			println(ex.getMessage(), true);
		}
		setSetting();
	}
	public void setDefuorutoConnection() throws BadLocationException {
		try {
			// （1）装载并注册数据库的JDBC驱动程序
			// 载入JDBC驱动：oracle安装目录下的jdbc\lib\classes12.jar
			println("正在试图加载驱动程序 " + driver);
			Class.forName(driver);
			println("驱动程序已加载");
			// 注册JDBC驱动：有些地方可不用此句
			println("url=" + url);
			println("user=" + user);
			println("password=" + password);
			println("正在试图连接数据库--------");
//			java.sql.DriverManager .registerDriver(new oracle.jdbc.driver.OracleDriver());
//			con = DriverManager.getConnection(url, user, password);
			
			DruidDataSource dataSource = new DruidDataSource();
			
			JMXUtils.register("com.alibaba:type=DruidDataSource", dataSource);
			dataSource.setInitialSize(10);
			dataSource.setMaxActive(25);
			dataSource.setMinIdle(15);
//			dataSource.setMaxIdle(30);
			dataSource.setPoolPreparedStatements(true);
			dataSource.setDriverClassName("oracle.jdbc.driver.OracleDriver");
			dataSource.setUrl(url);
			dataSource.setPoolPreparedStatements(true);
			dataSource.setUsername(user);
			dataSource.setPassword(password);
			dataSource.setValidationQuery("SELECT 'asus' from dual");
			dataSource.setTestOnBorrow(true);
			con=dataSource.getConnection();

 

			osd=new OracleSqlDetabese(con);	
			
				String sql = "select parent from " + shoukaiseki_insert_asset
						+ " WHERE location =? ";
				 psSelectParent = con.prepareStatement(sql);
				 
					sql = "select location,parent from " + lochierarchy
							+ " WHERE location =? and siteid='"+allsiteid+"'";
					psSelectLP = con.prepareStatement(sql);
					
					
			sql = "select id " + "from " + shoukaiseki_insert_asset
					+ " WHERE parent =? ";
			psSelectId = con.prepareStatement(sql);
			
			sql="select * from asset where assetnum=:assetnum and siteid=:siteid";
			findassetPS= con.prepareStatement(sql);
			
			sql="select * from "+shoukaiseki_insert_asset+" where asset=:assetnum and siteid=:siteid and sn<>:sn";
			findassettempPS= con.prepareStatement(sql);
			println("OK,成功连接到数据库");

			/**
			 * 关闭自动更新
			 */
			con.setAutoCommit(false);
		} catch (Exception ex) {
			ex.printStackTrace();
			println(ex.getMessage(), true);
		}
	}
	

	/**
	 * 新建子表
	 */
	public void hyou() {
		println("正在创建表............");
		sql = "CREATE TABLE " + shoukaiseki_insert_asset
				+ " (ID NUMBER NOT NULL ENABLE"
				+ ",LOCATION  VARCHAR2(30 CHAR)"
				+ ",DESCRIPTION  VARCHAR2(100 CHAR)"
				+ ",PARENT VARCHAR2(30 CHAR)" + " ,CFL NUMBER " // 本表编码重复
				+ " ,CFD NUMBER " // 本表名称重复
				+ " ,ALLCFL NUMBER " // 所有表编码重复
				+ " ,ALLCFD NUMBER " // 所有表名称重复
				+ ",SETUMEINUM  NUMBER"// EXCEL内编号
				+ ",SETUMEI  VARCHAR2(100 CHAR)"// 説明
				+ ",SETUMEI2  VARCHAR2(100 CHAR)"// 説明
				+ ")";
		println(sql);
		try {
			PreparedStatement pst = con.prepareStatement(sql);

			pst.execute();
			pst.close();// 更新后关闭此线程,不然更新数据多了就会异常
			printlnSeikou();
		} catch (Exception e) {
			e.printStackTrace();
			printlnSippai();
			jl.setText(shoukaiseki_insert_asset + "表创建失败");
			println("\n错误原因:\n" + e.getMessage());
			// TODO: handle exception
			return;
		}
		println(shoukaiseki_insert_asset + "表创建成功!");

	}

	public void kuRiKaeSu() {
		try {
			
			sql="select * from "+shoukaiseki_insert_asset+" order by asset";
			Statement tablebfb = con
					.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE,
							ResultSet.CONCUR_READ_ONLY);
			println(sql);
			ResultSet ablebr = tablebfb.executeQuery(sql);
			ablebr.last();
			int tablebc = ablebr.getRow(); // 获得当前行号：此处即为最大记录数 ,无效时为0

			Statement st = con.createStatement();
			ResultSet r = st.executeQuery(sql);

			println("需要更新的总记录数总共:" + tablebc);
			jl.setText("需要更新的总记录数总共:" + tablebc);
			endtj = 0;// 统计总更新数据条数

			Calendar cal = Calendar.getInstance();
			java.util.Date data = new java.util.Date();
			cal.setTime(data);// 设置时间为当前时间
			long timeOne = cal.getTimeInMillis();
			println("开始时间为:" + bartDateFormat.format(data));
			String xggymbak = "";// 相关工艺码对比字符
			String sbmcbak = "";
			int theerror = 0;
			double maxvalue = Math.pow(10D, assetnumSeqDigits);
			int updatecount = 0;

			while (r.next()) {
				error = 0;// 记录数据更新失败记录
				Boolean repaat = true;
				int sn = r.getInt("sn");// id
				String description = r.getString("DESCRIPTION");// 资产名称
				String assetnum = r.getString("asset");// 资产编码
				String siteid= r.getString("siteid");// 相关工艺码
				boolean tobeupdate=false;
				
				while(isNotUnique(assetnum,siteid,sn)){
					tobeupdate=true;
					String prefix=assetnum.substring(0, assetnum.length()-assetnumSeqDigits);
					String suffix=assetnum.substring(assetnum.length()-assetnumSeqDigits);
					try {
						
						if(!(Integer.parseInt(suffix)+1<maxvalue)){
							throw new Exception("流水号大于流水号位数的最大值,assetnum:"+assetnum+",最大值:"+maxvalue);
						}
						assetnum=prefix+String.format("%0"+assetnumSeqDigits+"d",Integer.parseInt(suffix)+1);
					} catch (Exception e) {
						// TODO: handle exception
						e.printStackTrace();
						throw e;
					}
					
				};
				if(tobeupdate){
					sql="update "+shoukaiseki_insert_asset+" set asset='"+assetnum+"' where sn="+sn;
					println("sql");
					update(con, sql);
					updatecount++;
				}

				if (error > 0) {
					theerror++;
				} else {
					endtj++;
					jl.setText("已成功更新:" + endtj + "条"
							+ df.format((float) endtj / tablebc));
				}
			}

			println("表  " + shoukaiseki_insert_asset + "  更新结束!");
			println("一共成功处理" + endtj + "次!");
			println("一共更新成功" + updatecount + "次!");
			println("一共更新失败" + theerror + "次!");
			data = new java.util.Date();
			cal.setTime(data);// 设置时间
			long timeTwo = cal.getTimeInMillis();
			long daysapart = (timeTwo - timeOne) / (1000 * 60);// 分钟
			long daysaparts = (timeTwo - timeOne) / 1000 % 60;// 获得总秒数后取除以60的余数即为秒数
			println("结束时间为:" + bartDateFormat.format(data));
			println("共花费时间为" + daysapart + "分" + daysaparts + "秒");
			printlnSeikou();
		}

		catch (Exception ex) {
			ex.printStackTrace();
			println(ex.getMessage());
			println(sql);
			printlnSippai();
		}
	}
	
	/**
	 * @param assetnum
	 * @param siteid
	 * @param sn
	 * @return false:没有重复值,true:非唯一,需要修改
	 * @throws SQLException 
	 */
	public boolean isNotUnique(String assetnum,String siteid,int sn) throws SQLException{
		boolean isunique=false;
		findassetPS.setString(1,assetnum );	
		findassetPS.setString(2,siteid);
		findassetRS = findassetPS.executeQuery();
		if(findassetRS.next()){
			findassetRS.close();
			isunique=true;
			println("assetnum:"+assetnum+",siteid:"+siteid);
		}
		
		findassettempPS.setString(1,assetnum );	
		findassettempPS.setString(2,siteid);
		findassettempPS.setInt(3,sn);
		findassetRS = findassettempPS.executeQuery();
		if(findassetRS.next()){
			findassetRS.close();
			isunique=true;
			println("assetnum:"+assetnum+",siteid:"+siteid+",sn:"+sn);
		}
		
		return isunique;
		
	}

	



	/**
	 * 删除表按钮执行方法
	 */
	public void delHyou() {
		println("正在删除表............");
		sql = "DROP TABLE  " + shoukaiseki_insert_asset;
		println(sql);
		try {
			PreparedStatement pst = con.prepareStatement(sql);
			int suteetasu = JOptionPane.showConfirmDialog(null, "确定删除该表吗?",
					"提示!!", JOptionPane.YES_NO_OPTION);
			if (suteetasu == 0) {
				pst.execute();
				pst.close();// 更新后关闭此线程,不然更新数据多了就会异常
				println(shoukaiseki_insert_asset + "表删除成功!");
			} else {
				println(shoukaiseki_insert_asset + "表已取消删除!");
			}
			printlnSeikou();
		} catch (Exception e) {
			e.printStackTrace();
			println(sql);
			jl.setText(shoukaiseki_insert_asset + "表删除失败");
			println("\n错误原因:\n" + e.getMessage());
			printlnSippai();
			// TODO: handle exception
			return;
		}
	}

	/**
	 * 表内查询依赖关系,即寻找父索引
	 */
	public void yilai() {
		try {

			sql = "select   * " + "from " + shoukaiseki_insert_asset + " a "
					+ "where parent is null ";
			Statement tablebfb = con
					.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE,
							ResultSet.CONCUR_READ_ONLY);
			println(sql);
			ResultSet ablebr = tablebfb.executeQuery(sql);
			ablebr.last();
			int tablebc = ablebr.getRow(); // 获得当前行号：此处即为最大记录数 ,无效时为0

			Statement st = con.createStatement();
			ResultSet r = st.executeQuery(sql);

			println("需要更新的总记录数总共:" + tablebc);
			jl.setText("需要更新的总记录数总共:" + tablebc);
			endtj = 0;// 统计总更新数据条数

			Calendar cal = Calendar.getInstance();
			java.util.Date data = new java.util.Date();
			cal.setTime(data);// 设置时间为当前时间
			long timeOne = cal.getTimeInMillis();
			println("开始时间为:" + bartDateFormat.format(data));
			int intddcsm = 10000;
			int intddcsmbak = 10000;
			String xggymbak = "";// 相关工艺码对比字符
			String sbmcbak = "";
			int theerror = 0;
			String parent = "";// 相关工艺码的父级编号

			while (r.next()) {
				int id = r.getInt("id");// id
				String xggym = r.getString("location");// 相关工艺码
				parent = xggym.substring(0, xggym.length());// 上层索引码,先置于于工艺码相同
				// int theerror = r.getInt(7);//错误标识

				Boolean theok = false;// 如果为真,保留索引名,为假则置空字符
				if (parent.length() > parentCutLength) {
					parent = xggym.substring(0, xggym.length()
							- parentCutLength);// 索引的父值比子值至少小于两位以上
					System.out.println(xggym+"--"+parent);
				}
				if (xggym.length() < parentLength) {
					parent = xggym.substring(0, 1);// 工艺码小于parentLength位则取父值首字母
					theok = true;
				} else {
					Statement thissa = con.createStatement(
							ResultSet.TYPE_SCROLL_SENSITIVE,
							ResultSet.CONCUR_READ_ONLY);
					for (int i = 1; parent.length() > parentLowerLength; i++) {
						sql = "select *" + "from " + shoukaiseki_insert_asset
								+ " WHERE location ='" + parent + "' " 
										//+" and COLUMN1= '"+r.getString("COLUMN1")+"'"
										;
						 System.out.println(sql);
						ResultSet thisa = thissa.executeQuery(sql);
						thisa.last();
						int thisd = thisa.getRow();// 获得当前行号：此处即为最大记录数 ,无效时为0
						thisa.close();// 更新后关闭此线程,不然更新数据多了就会异常
						if (thisd > 0) {
							theok = true;
							break;
						}

						parent = xggym.substring(0, xggym.length() - i);
					}
					thissa.close();// 更新后关闭此线程,不然更新数据多了就会异常
				}
				if (theok == false) {
//					parent = "xxxxxxx";
				}
				updateParent(con, shoukaiseki_insert_asset, id, parent);
				// tablezb);//更新表数据
				if (error > 0) {
					theerror++;
				} else {
					endtj++;
					jl.setText("已成功更新:" + endtj + "条"
							+ df.format((float) endtj / tablebc));

				}
			}

			println("表  " + shoukaiseki_insert_asset + "  更新结束!");
			println("一共更新成功" + endtj + "次!");
			println("一共更新失败" + theerror + "次!");
			data = new java.util.Date();
			cal.setTime(data);// 设置时间
			long timeTwo = cal.getTimeInMillis();
			long daysapart = (timeTwo - timeOne) / (1000 * 60);// 分钟
			long daysaparts = (timeTwo - timeOne) / 1000 % 60;// 获得总秒数后取除以60的余数即为秒数
			println("结束时间为:" + bartDateFormat.format(data));
			println("共花费时间为" + daysapart + "分" + daysaparts + "秒");
			printlnSeikou();
		} catch (Exception e) {
			// TODO: handle exception
			println(e.getMessage());
			println(sql);
			printlnSippai();
		}
	}

	/**
	 * 更新Parent值
	 * 
	 * @param con
	 * @param shoukaiseki_insert_kks
	 * @param id
	 * @param parent
	 * @throws SQLException
	 */
	public void updateParent(Connection con, String shoukaiseki_insert_kks,
			int id, String parent) throws SQLException {
		String command = "";
		try {
			command = "UPDATE " + shoukaiseki_insert_kks + " SET  "
					+ " parent = '" + parent + "'" + " WHERE   id=" + id;
			// System.out.println(command);
			PreparedStatement pst = con.prepareStatement(command);
			pst.execute();
			pst.close();// 更新后关闭此线程,不然更新数据多了就会异常

		} catch (Exception e) {
			e.printStackTrace();
			error++;
			println(command);
			println("" + error);
			println(e.getMessage());
			// TODO: handle exception
		}
	}

	public void allYilai() {
		sql = "";
		try {

			sql = "select   * " + "from " + shoukaiseki_insert_asset + " a "
					+ "where parent is null";
			Statement tablebfb = con
					.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE,
							ResultSet.CONCUR_READ_ONLY);
			println(sql);
			ResultSet ablebr = tablebfb.executeQuery(sql);
			ablebr.last();
			int tablebc = ablebr.getRow(); // 获得当前行号：此处即为最大记录数 ,无效时为0

			Statement st = con.createStatement();
			ResultSet r = st.executeQuery(sql);

			println("需要更新的总记录数总共:" + tablebc);
			jl.setText("需要更新的总记录数总共:" + tablebc);
			endtj = 0;// 统计总更新数据条数

			Calendar cal = Calendar.getInstance();
			java.util.Date data = new java.util.Date();
			cal.setTime(data);// 设置时间为当前时间
			long timeOne = cal.getTimeInMillis();
			println("开始时间为:" + bartDateFormat.format(data));
			int intddcsm = 10000;
			int intddcsmbak = 10000;
			String xggymbak = "";// 相关工艺码对比字符
			String sbmcbak = "";
			int theerror = 0;
			String parent = "";// 相关工艺码的父级编号

			while (r.next()) {
				int id = r.getInt("id");// id
				String xggym = r.getString("location");// 相关工艺码
				parent = xggym.substring(0, xggym.length());// 上层索引码,先置于于工艺码相同
				// int theerror = r.getInt(7);//错误标识

				Boolean theok = false;// 如果为真,保留索引名,为假则置空字符
				if (parent.length() > parentCutLength) {
					parent = xggym.substring(0, xggym.length()
							- parentCutLength);// 索引的父值比子值至少小于两位以上
				}
				if (xggym.length() < parentLength) {
					parent = xggym.substring(0, 1);// 工艺码小于parentLength位则取父值首字母
					theok = true;
				} else {
					Statement thissa = con.createStatement(
							ResultSet.TYPE_SCROLL_SENSITIVE,
							ResultSet.CONCUR_READ_ONLY);
					for (int i = 1; parent.length() > parentLowerLength; i++) {
						sql = "select *" + "from " + asset
								+ " WHERE location ='" + parent + "' ";
						// System.out.println(thiscommand);
						ResultSet thisa = thissa.executeQuery(sql);
						thisa.last();
						int thisd = thisa.getRow();// 获得当前行号：此处即为最大记录数 ,无效时为0
						thisa.close();// 更新后关闭此线程,不然更新数据多了就会异常
						if (thisd > 0) {
							theok = true;
							break;
						}

						parent = xggym.substring(0, xggym.length() - i);
					}
					thissa.close();// 更新后关闭此线程,不然更新数据多了就会异常
				}
				if (theok == false) {
					parent = "xxxxxxx";
				}
				updateParent(con, shoukaiseki_insert_asset, id, parent);
				// tablezb);//更新表数据
				if (error > 0) {
					theerror++;
				} else {
					endtj++;
					jl.setText("已成功更新:" + endtj + "条"
							+ df.format((float) endtj / tablebc));

				}
			}

			println("表  " + shoukaiseki_insert_asset + "  更新结束!");
			println("一共更新成功" + endtj + "次!");
			println("一共更新失败" + theerror + "次!");
			data = new java.util.Date();
			cal.setTime(data);// 设置时间
			long timeTwo = cal.getTimeInMillis();
			long daysapart = (timeTwo - timeOne) / (1000 * 60);// 分钟
			long daysaparts = (timeTwo - timeOne) / 1000 % 60;// 获得总秒数后取除以60的余数即为秒数
			println("结束时间为:" + bartDateFormat.format(data));
			println("共花费时间为" + daysapart + "分" + daysaparts + "秒");
			printlnSeikou();
		} catch (Exception e) {
			// TODO: handle exception
			println(e.getMessage());
			println(sql);
			printlnSippai();
		}
	}

	/**
	 * 清字段首位空
	 * 
	 * @throws BadLocationException
	 */
	public void delLDTrim() throws BadLocationException {
		sql = "";
		try {

			Calendar cal = Calendar.getInstance();
			java.util.Date data = new java.util.Date();
			cal.setTime(data);// 设置时间为当前时间
			long timeOne = cal.getTimeInMillis();
			println("开始时间为:" + bartDateFormat.format(data));

			/**
			 * 清 asset 字段首尾空
			 */
			println("清 asset 字段首尾空");
			sql = "UPDATE " + shoukaiseki_insert_asset
					+ " SET asset = trim(asset) ";
			println(sql);
			update(con, sql);
			println("清 asset 字段首尾空完成\n");
			/**
			 * 清DESCRIPTION字段首尾空
			 */
			println("清 description 字段首尾空");
			sql = "UPDATE " + shoukaiseki_insert_asset
					+ " SET DESCRIPTION = trim(DESCRIPTION) ";
			println(sql);
			update(con, sql);
			println("清 description 字段首尾空完成\n");
			
			/**
			 * 清 siteid 字段首尾空
			 */
			println("清 siteid 字段首尾空");
			sql = "UPDATE " + shoukaiseki_insert_asset
					+ " SET siteid = trim(siteid) ";
			println(sql);
			update(con, sql);
			println("清 siteid 字段首尾空完成\n");

			println("表  " + shoukaiseki_insert_asset + "  清首尾空结束!");
			data = new java.util.Date();
			cal.setTime(data);// 设置时间
			long timeTwo = cal.getTimeInMillis();
			long daysapart = (timeTwo - timeOne) / (1000 * 60);// 分钟
			long daysaparts = (timeTwo - timeOne) / 1000 % 60;// 获得总秒数后取除以60的余数即为秒数
			println("结束时间为:" + bartDateFormat.format(data));
			println("共花费时间为" + daysapart + "分" + daysaparts + "秒");
			printlnSeikou();
		} catch (Exception e) {
			// TODO: handle exception
			println(e.getMessage());
			println(sql);
			printlnSippai();
		}

	}

	/**
	 * 更新命令,成功执行后返回true
	 * 
	 * @param con
	 * @param sql
	 */
	public boolean update(Connection con, String sql) {
		try {
			PreparedStatement pst = con.prepareStatement(sql);
			pst.execute();
			pst.close();// 更新后关闭此线程,不然更新数据多了就会异常
		} catch (Exception e) {
			e.printStackTrace();
			error++;
			println("" + error);
			println(e.getMessage());
			println(sql);
			// TODO: handle exception
			return false;
		}
		return true;
	}


	/**
	 * 输出SQL命令成功信息
	 * 
	 * @throws BadLocationException
	 */
	public void printlnSeikou() {
			println("------------成功执行更新完毕!-----------", true);
	}

	/**
	 * 输出SQL命令失败信息
	 * 
	 * @throws BadLocationException
	 */
	public void printlnSippai() {

			println("------------更新错误??????-----------", true);
	}


	




	public String getassetSQL(){
		sql = "insert into "
			+ asset
			+ " (Location,Description,Type,ChangeBy,ChangeDate,"
			+ "Disabled,SiteId,Orgid,Status,Langcode,ISDEFAULT,"
			+ "assetID,USEINPOPR,HASLD,AUTOWOGEN,STATUSDATE"
			+",ISREPAIRFACILITY,PLUSCLOOP,PLUSCPMEXTDATE"
			+" ) values( ?,?,"
			+ // Description
			"'"+type+"','MAXADMIN',"
			+ // ChangeBy
			datetime+","
			+ // ChangeDate
			"0,?," + // SiteId
			"?," + // Orgid
			"'"+status+"'," + // Status
			"'ZH'," + // Langcode
			"'0'," + "assetseq.nextval," + // assetid
			"'0'," + "'0'," + "'0'" +
					","+datetime +
					",'0','0','0' )";
		return sql;
	}
	public void updateasset(Connection con, String description,
			String location,String siteid,String orgid,PreparedStatement pst) throws SQLException {
		try {
			pst.setString(1,location);
			pst.setString(2,description);
			pst.setString(3,siteid);
			pst.setString(4,orgid);
			pst.addBatch();
//			savesql.addLastLine(sql);
		} catch (Exception e) {
			error++;
			e.printStackTrace();
			println(e.getMessage());
			System.out.println(sql);
			println(sql);
			// TODO: handle exception
		}
	}


	/**
	 * 查询表中的parent字段是否有空值,有返回false
	 * 
	 * @return
	 * @throws BadLocationException
	 */
	public boolean selectParentHaveNull() throws BadLocationException {
		try {
			sql = "select  * from " + shoukaiseki_insert_asset
					+ " where trim(parent) is null";
			Statement tablebfb = con
					.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE,
							ResultSet.CONCUR_READ_ONLY);
			println(sql);
			ResultSet ablebr = tablebfb.executeQuery(sql);
			ablebr.last();
			int tablebc = ablebr.getRow(); // 获得当前行号：此处即为最大记录数 ,无效时为0
			if (tablebc > 0) {
				JOptionPane.showMessageDialog(getContentPane(), "表"
						+ shoukaiseki_insert_asset + "的parent字段含有空值,终止执行!",
						"错误提示!", JOptionPane.ERROR_MESSAGE);
				println("表" + shoukaiseki_insert_asset
						+ "的parent字段含有空值,终止执行!");
				return false;
			}
			printlnSeikou();
		} catch (Exception e) {
			println(e.getMessage());
			println(sql);
			printlnSippai();
			return false;
			// TODO: handle exception
		}

		return true;
	}


	public void readSetting() throws IOException, BadLocationException {
		newFile();// 无配置文件则自动新建文件
		reFile();// 读取配置文件
		setSetting();//
//		setCon();// 载入驱动
	}

	public void newFile() throws IOException {
		// 新建文件
		if (!txtfile.exists()) {
			if (txtfile.createNewFile()) {
				println("配置文件创建成功!");
				wrFile();// 写入文件
			} else {
				println("创建新文件失败!");
			}
		} else {
			println("发现配置文件" + fileName + "!");
		}
	}


	/**
	 * 生成ImpKksGui.sql
	 */
	public void wrFileSql() {
		println("正在创建文件" + sqlNameFile.getPath());
		try {
			// 常量类:各编码名称
			CharacterEncodingName ce = new CharacterEncodingName();
			FileOutputStream o = new FileOutputStream(sqlNameFile);
			// 采用UTF-8编码格式输出
			OutputStreamWriter out = new OutputStreamWriter(o, ce.UTF_8);
//			out.write(savesql.getContent());
			println("文件创建写入成功");
			out.close();
		} catch (Exception e) {
			// TODO: handle exception
			println(e.getMessage());
		}
	}
	
	public void wrFile() {
		println("正在创建文件" + txtfile.getPath());
		try {
			String age0 = wrString();
			// 常量类:各编码名称
			CharacterEncodingName ce = new CharacterEncodingName();
			FileOutputStream o = new FileOutputStream(txtfile);
			// 采用UTF-8编码格式输出
			OutputStreamWriter out = new OutputStreamWriter(o, ce.UTF_8);
			out.write(age0);
			println("文件创建写入成功");
			out.close();
		} catch (Exception e) {
			// TODO: handle exception
			println(e.getMessage());
		}
	}

	private void reFile() throws BadLocationException {
		// 读取文件
		println("\n\n读取文件!");
		try {
			String code = CharacterEncoding.getLocalteFileEncode(fileName);

			FileInputStream in = new FileInputStream(fileName);
			BufferedReader br = new BufferedReader(new InputStreamReader(in,
					code));
			/**
			 * 解决win记事本保存UTF-8文件后文件头???问题, bom信息=EFBBBF
			 */
			br = new BufferedReader(new UnicodeReader(in, code));

			System.out.println("code=" + code);

			String sr = null;
			String a = null;
			String b = null;
			while ((sr = br.readLine()) != null) {
				println(sr);
				if (sr.isEmpty()) {
					continue;
				}
				a = sr.substring(0, 1);
				System.out.println(a);
				System.out.println(sr);
				if (a.equals("#")) {
					continue;
				}
				// 取等号位置
				int value = sr.indexOf("=");
				System.out.println(value);
				if (value < 0) {
					continue;
				}
				a = sr.substring(0, value).trim();// =号前面取首尾空
				b = sr.substring(value + 1, sr.length()).trim();// =号后面取首尾空
				if (a.equals("url")) {
					url = b;
					continue;
				}
				if (a.equals("driver")) {
					driver = b;
					continue;
				}
				if (a.equals("user")) {
					user = b;
					continue;
				}
				if (a.equals("password")) {
					password = b;
					continue;
				}
				if (a.equals("assettemp")) {
					shoukaiseki_insert_asset = b;
					continue;
				}
				if (a.equalsIgnoreCase("assetnumSeqDigits")) {
					assetnumSeqDigits = Integer.parseInt(b);
					continue;
				}
				
			}
		} catch (IOException e) {
			e.printStackTrace();
			println(e.getMessage());
		}
		println("url=" + url, true);
		println("driver=" + driver, true);
		println("user=" + user, true);
		println("password=" + password, true);
//		println("parent=" + parentOne, true);
//		println("asset=" + asset, true);
		println( "assettemp=" + shoukaiseki_insert_asset, true);
		println( "assetnumSeqDigits=" + assetnumSeqDigits, true);
		
//		println("parentLength=" + parentLength, true);
//		println("parentCutLength=" + parentCutLength, true);
//		println("parentLowerLength=" + parentLowerLength, true);

	}

	/**
	 * 默认值
	 * 
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public String wrString() throws UnsupportedEncodingException {
		String s = "";
		s = s + "#注释符号为#\r\n";
		s = s + "#注意要区分大小写\r\n";
		s = s + "#连接数据库参数\r\n";
		s = s + "url=jdbc:oracle:thin:@localhost:1521:orcl\r\n";
		s = s + "driver=oracle.jdbc.OracleDriver\r\n";
		s = s + "user=maximo\r\n";
		s = s + "password=maximo\r\n";
		s = s + "#KKS新增编码存放的表\r\n";
		s = s + "assettemp=shoukaiseki_insert_asset\r\n";
		s = s + "#编码分类流水号的位数,例如 0203040440001 ,前几位(020304044)为分类,后4位(0001)为流水号\r\n";
		s = s + "assetnumSeqDigits=4\r\n";
		
		return s;
	}

	public void setSetting() {
	}
	
	/**
	 * con提交更改
	 * 要事先con.setAutoCommit(false);自动更新关闭掉
	 */
	public void conCommit(){
		try {
			int suteetasu = JOptionPane.showConfirmDialog(null,
						"确定要提交更改吗?\n取消后将不提交,但是也不会取消更改.", "提示!!",
						JOptionPane.YES_NO_OPTION);
			if (suteetasu == 0) {
				con.commit();
				println("**********提交成功!**********",true);
				commitButton.setEnabled(false);
				rollbackButton.setEnabled(false);
			}else{
				println("**********已取消提交!**********",true);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			conRollback();
			e.printStackTrace();
		}
	}
	/**
	 * @throws BadLocationException 
	 * 
	 */
	public void conRollback(){
			try {
				con.rollback();
				commitButton.setEnabled(false);
				rollbackButton.setEnabled(false);
				println("**********本地更改已清除!**********", true);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

	public void println() {
		println("");
	}
	
	public void println(int age) {
		println("" + age);
	}

	public void println(String age)  {
		try {
			textPane.addLastLine(age, false);
		} catch (BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void println(String age, boolean b) {
		try {
			textPane.addLastLine(age, b);
		} catch (BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public void printConnection() throws BadLocationException{
		println("url=" + url, true);
		println("driver=" + driver, true);
		println("user=" + user, true);
		println("password=" + password, true);
	}
}




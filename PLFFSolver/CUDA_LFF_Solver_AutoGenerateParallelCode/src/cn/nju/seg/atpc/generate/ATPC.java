package cn.nju.seg.atpc.generate;


public class ATPC
{
	/**
	 * 确定自动生成的CUDA代码的数据类型，分为两部分
	 * 1）双精度double--------true
	 * 2）单精度float---------false
	 *  这个变量为true的时候表示自动生成双精度的并行代码，否则自动生成单精度的并行代码
	 */
	public static boolean forDouble = false;
	
	/**
	 * 确定是否使用流并行
	 * 1）使用------------true
	 * 2）不使用----------false
	 */
	public static boolean useStream = true;
	
	
	/**
	 * 确定不等式简单约束（不包含等式约束）是否做一个简单的化简
	 * eg log(x) > log(y) <===> x>y && x>0 && y>0
	 * 1）使用------------true ： 因为化简会产生更多的约束，但是每一个约束都变得简单了
	 * 2）不使用----------false： 直接计算复杂约束的运行时刻值即可
	 */
	public static boolean shouldSimplification = false;
	
	
	/**
	 * 确定是否显示线性拟合函数的图像走势
	 * 1）显示------------true
	 * 2）不显示----------false
	 */
	public static boolean showLFFGUI = false;
	
		
}

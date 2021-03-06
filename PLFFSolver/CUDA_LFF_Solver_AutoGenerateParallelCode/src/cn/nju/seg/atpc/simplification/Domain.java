package cn.nju.seg.atpc.simplification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.cdt.core.dom.ast.IASTBinaryExpression;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTIdExpression;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTExpressionList;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTFunctionCallExpression;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTLiteralExpression;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTUnaryExpression;

import cn.nju.seg.atpc.generate.ATPC;
import cn.nju.seg.atpc.main.ConstraintParameter;
import cn.nju.seg.atpc.model.BinaryExpression;


/**
 * 初始区间的预测和计算
 * @author zhanghui
 */

@SuppressWarnings("restriction")
public class Domain
{
	/*
	 * 保存在要处理的约束
	 * 合取子式
	 * */
	public List<BinaryExpression> target = null;
	
	
	/*
	 * 这个map是一个映射函数，保存的是对应变量的区间
	 * */
	public Map<String, List<Interval>> allVarInterval = new HashMap<>();
	
	/*
	 * 这个记录对应Interval的约束的Op
	 * */
	public Map<String, Set<String>> allOp = new HashMap<>();
	
	
	/*
	 * 构造函数
	 * */
	public Domain(List<BinaryExpression> target)
	{
		this.target = target;
		
		String[] varName = ConstraintParameter.parameterNames;
		for(int i=0;i<varName.length;i++)
		{
			Set<String> set = new HashSet<String>();
			allOp.put(varName[i], set);
			
			List<Interval> list = new ArrayList<Interval>();
			allVarInterval.put(varName[i], list);
		}
	}
	
	
	/*
	 * 初始区间的计算，这里只考虑析取范式的第一个合取子式，也就是我们求解的目标
	 * */
	public void calaDomain()
	{
		//使用DFS深度优先遍历来获取相关变量的定义域范围
		for(BinaryExpression one : this.target)
		{
			useAllBasicFuncDomains(one.getIastExpression());
		}
		
		//根据线性约束的规则来求解
		for(BinaryExpression one : this.target)
		{
			useLinearFunctionRule(one.getIastExpression());
		}
		
		 mergeDomain();
		 
		//printConstraint();
	}
	
	/*
	 * 计算最终的交区间
	 * */
	public void mergeDomain()
	{
		String[] varName = ConstraintParameter.parameterNames;
		for(int i=0;i<varName.length;i++)
		{
			List<Interval> one = allVarInterval.get(varName[i]);
			Interval res = intersectionInterval(one);
			ConstraintParameter.allVarDomain.add(res);
			if(res.left > res.right)
			{
				String info = "变量"+varName[i]+"存在相互矛盾的定义域: ( "+res.left +" , "+ res.right + " ) , "
						+ "也即存在最小不可解集合，所以无解\n";
				ConstraintParameter.noSolutionInfo += info;
				ConstraintParameter.hasSolution = false;
			}
		}
		
/*		System.out.println("所有变量的求解结果定义域如下：");
		for(int i=0;i<varName.length;i++)
		{
			System.out.println(varName[i]+" ( "+ConstraintParameter.allVarDomain.get(i).left
					+" , "+ConstraintParameter.allVarDomain.get(i).right+" ) ");
		}
		System.out.println();*/
	}
	
	/*
	 * 给每一个变量计算定义域
	 * */
	public Interval intersectionInterval(List<Interval> one)
	{
		Interval res = new Interval(ConstraintParameter.minNum,ConstraintParameter.maxNum);
		if(one.size()<=0)
			return res;
		else
		{
			double left = ConstraintParameter.minNum , right = ConstraintParameter.maxNum;
			for(int i=0;i<one.size();i++)
			{
				left = Math.max(left, one.get(i).left);
				right = Math.min(right, one.get(i).right); 
			}
			
			res.left = left;
			res.right = right;
			return res;
		}
	}


	/*
	 * 通过深度优先遍历来找到所有的函数声明的地方，然后求解定义域
	 * */
	@SuppressWarnings("deprecation")
	public void useAllBasicFuncDomains(IASTExpression iastExpression)
	{
		//遇到ID或者字面常量直接return 
		 if(iastExpression instanceof IASTIdExpression || iastExpression instanceof CPPASTLiteralExpression)
			 return ;
		 else if(iastExpression instanceof CPPASTUnaryExpression)//一元表达式
		 {
			 //一元表达式直接递归查找
			 CPPASTUnaryExpression tmp  = (CPPASTUnaryExpression)iastExpression;
			 for(IASTNode iastNode : tmp.getChildren())
				 useAllBasicFuncDomains((IASTExpression)iastNode);
			 
		 }else if(iastExpression instanceof IASTBinaryExpression)//二元表达式
		 {
			 //二元表达式递归查找
			 useAllBasicFuncDomains(((IASTBinaryExpression)iastExpression).getOperand1());
			 useAllBasicFuncDomains(((IASTBinaryExpression)iastExpression).getOperand2());
			
		 }else if(iastExpression instanceof CPPASTFunctionCallExpression)//函数调用，比如tan
		 {
			 //遇到函数调用需要处理
			CPPASTFunctionCallExpression tmp = (CPPASTFunctionCallExpression)iastExpression;
			IASTExpression para = tmp.getParameterExpression();
			
			//如果参数是一个复合类型，直接递归
			if(para instanceof CPPASTExpressionList)
			{
				IASTExpression[] children = ((CPPASTExpressionList) para).getExpressions();
				for(IASTExpression kid : children)
					useAllBasicFuncDomains(kid);
			}
			else if(isID(para))
			{
				//遇到ID，这个时候需要判断相关函数的以及对应的自变量的定义域
				String func = tmp.getFunctionNameExpression().getRawSignature();
				String var = para.getRawSignature();
				
/*				System.out.println(iastExpression.getRawSignature()+"  IS  A Fucntion CAll " + func+"  "+func.length()
						+"   "+var+"   "+var.length());*/
				
				if(allVarInterval.containsKey(var))
				{
					Interval res = getDomain(func);
					String op = getBasicFucnOp(func);
					if(res!=null && op != null)
					{
						//System.out.println("Func Domain: ("+res.left+" , "+res.right+" ) , ");
						allVarInterval.get(var).add(res);
						allOp.get(var).add(op);
					}
				}else
				{
					System.out.println("public void calaAllBasicFuncDomains(IASTExpression iastExpression) "
							+"  "+tmp.getRawSignature()+"  出现了不应该的变量");
				}
			}else
			{
				//其余的直接return即可
				return ;
			}
		}
		else 
		{
			System.out.println("public void calaAllBasicFuncDomains(IASTExpression iastExpression):  "
		                     +"在计算基本初等函数的定义域的时候出现问题"+iastExpression.getRawSignature()+" ");
		}
	}
	
	/*
	 * 计算基本初等函数的定义域
	 * */
	public Interval getDomain(String func) 
	{
		if(func.contains("sin") || func.contains("cos") || func.contains("tan"))
			return null;
		else if(func.equals("ceil") || func.equals("floor") || func.equals("round"))
			return null;
		else if(func.contains("exp"))
			return null;
		else if(func.equals("pow"))
			return null;
		else if(func.contains("log") || func.equals("sqrt"))
		{
			Interval res = new Interval(0, ConstraintParameter.maxNum);
			return res;
		}else 
		{
			System.out.println("public Interval getDomain(String func)  "
					+ " 出现了新的类型的函数调用，需要可能需要扩展定义域的分析规则，"
					+ " FunctionName：  "+func);
			return null;
		}
	}

	/*
	 * 计算基本初等函数的定义域的对应的op
	 * */
	public String getBasicFucnOp(String func)
	{
		if(func.contains("sin") || func.contains("cos") || func.contains("tan"))
			return null;
		else if(func.equals("ceil") || func.equals("floor") || func.equals("round"))
			return null;
		else if(func.contains("exp"))
			return null;
		else if(func.equals("pow"))
			return null;
		else if(func.contains("log"))
			return ">";
		else if(func.equals("sqrt"))
			return ">=";
		else 
		{
			System.out.println("public Interval getDomain(String func)  "
					+ " 出现了新的类型的函数调用，需要可能需要扩展定义域的分析规则，"
					+ " FunctionName：  "+func);
			return null;
		}
	}
	
	
	/*
	 * 这个函数是直接处理线性不等式的约束求解，主要设计下面集中规则
	 * Case0的处理情况:x<3 , x<=-1 , x>-3 , x>=1 , 1<x , -1<=x , 2>x , -2>=x
	 * 至于想Case1比如 -x>3 , -a*x < 5 这种暂时不处理了，因为可以通过线性拟合指导来操作
	 * 假如要添加其他的case，直接在这个函数中识别即可 
	 * */
	public void useLinearFunctionRule(IASTExpression iastExpression)
	{
		IASTBinaryExpression tmp = (IASTBinaryExpression)iastExpression;
		//dealWithCase0(tmp);
	}

	/*
	 * Case0：的处理，
	 * 也就是 x<1 ,1<x 这两种简单的情况
	 * */
	public void dealWithCase0(IASTBinaryExpression iast) 
	{
		Interval res = null;
		IASTExpression op1 = removeBracket(iast.getOperand1());
		IASTExpression op2 = removeBracket(iast.getOperand2());
		
		String var = "" , op = "";
		if(isID(op1) == true && isNum(op2) == true)
		{
			var = op1.getRawSignature();
			if(iast.getOperator() == IASTBinaryExpression.op_greaterEqual || iast.getOperator() == IASTBinaryExpression.op_greaterThan)
			{
				//x >= a
				double left = Double.parseDouble(op2.getRawSignature());
				res = new Interval(left, ConstraintParameter.maxNum);
				op = iast.getOperator() == IASTBinaryExpression.op_greaterEqual ? ">=" : ">" ;
			}else if(iast.getOperator() == IASTBinaryExpression.op_lessEqual || iast.getOperator() == IASTBinaryExpression.op_lessThan)
			{
				//x <= a
				double right = Double.parseDouble(op2.getRawSignature());
				res = new Interval(ConstraintParameter.minNum,right);
				op = iast.getOperator() == IASTBinaryExpression.op_lessEqual ? "<=" : "<" ;
			}
			
		}else if(isNum(op1) == true && isID(op2) == true)
		{
			var = op2.getRawSignature();
			if(iast.getOperator() == IASTBinaryExpression.op_greaterEqual || iast.getOperator() == IASTBinaryExpression.op_greaterThan)
			{
				//a >= x
				double right = Double.parseDouble(op1.getRawSignature());
				res = new Interval(ConstraintParameter.minNum,right);
				op = iast.getOperator() == IASTBinaryExpression.op_greaterEqual ? ">=" : ">" ;
			}else if(iast.getOperator() == IASTBinaryExpression.op_lessEqual || iast.getOperator() == IASTBinaryExpression.op_lessThan)
			{
				//a <= x
				double left = Double.parseDouble(op1.getRawSignature());
				res = new Interval(left, ConstraintParameter.maxNum);
				op = iast.getOperator() == IASTBinaryExpression.op_lessEqual ? "<=" : "<" ;
			}
		}
		
		if(res!=null)
		{
			if(allVarInterval.containsKey(var)==true)
			{
				allVarInterval.get(var).add(res);
				allOp.get(var).add(op);
				//System.out.println("Expression: ( "+res.left +" , "+res.right+" )");
			}
			else
				System.out.println("public void dealWithCase0(IASTBinaryExpression iast)   可能出现了错误，无法找到变量"
			    + var +"    "+iast.getRawSignature());
		}
	}
	
	
	/*
	 * 去除括号处理
	 * */
	public static IASTExpression removeBracket(IASTExpression iast)
	{
		while( iast instanceof CPPASTUnaryExpression)
		{
			String tmp = iast.getRawSignature().replace(" ", "");
			if(tmp.length() > 0 && tmp.charAt(0) == '(')
				iast = (IASTExpression)(iast.getChildren()[0]);
			else
				break;
		}
		
		return iast;
	}
	
	/*
	 * 判断一个函数是否是ID,
	 * */
	public static boolean isID(IASTExpression op) 
	{
		op = removeBracket(op);
		if(op instanceof IASTIdExpression)
			return true;
		else
			return false;
	}


	/*
	 * 判断一个函数是否是数字，这个要区分为是否为整数，因为它们的AST是不一样的
	 * */
	public static boolean isNum(IASTExpression op) 
	{
		op = removeBracket(op);
		//处理直接是整数的情况
		if(op instanceof CPPASTLiteralExpression)
			return true;
		else 
		{
			//处理负数情况
			if(op instanceof CPPASTUnaryExpression)
			{
				IASTExpression i = op;
				while( i instanceof CPPASTUnaryExpression)
					i = (IASTExpression)(i.getChildren()[0]);
				if(i instanceof CPPASTLiteralExpression)
					return true;
				else
					return false;
			}else 
				return false;
		}
	}


	/**
	 * JZ
	 * 打印析取范式
	 */
	public void printConstraint()
	{
		for(int i=0;i<target.size();i++)
		{
			printConstraintInfo(target.get(i).getIastExpression());
			System.out.println();
		}
		System.out.println();
	}
	
	
	public static void printConstraintInfo(IASTExpression iastExpression) 
	{
		 if(iastExpression instanceof IASTIdExpression)//ID
		 {
			 System.out.println(iastExpression.getRawSignature()+" Is An ID ");
		 }
		 else if(iastExpression instanceof CPPASTLiteralExpression) //常数
		 {
			 System.out.println(iastExpression.getRawSignature()+" Is An Literal ");
		 }
		 else if(iastExpression instanceof CPPASTUnaryExpression)//一元表达式
		 {
			 CPPASTUnaryExpression tmp  = (CPPASTUnaryExpression)iastExpression;
			 System.out.println(iastExpression.getRawSignature()+" Is An CPPASTUnaryExpression "
			+ " Childre Size:  "+tmp.getChildren().length);
			 for(IASTNode iastNode : tmp.getChildren())
				 printConstraintInfo((IASTExpression)iastNode);
			 
		 }else if(iastExpression instanceof IASTBinaryExpression)//二元表达式
		 {
			System.out.println(iastExpression.getRawSignature());
			printConstraintInfo(((IASTBinaryExpression)iastExpression).getOperand1());
			printConstraintInfo(((IASTBinaryExpression)iastExpression).getOperand2());
			
		 }else if(iastExpression instanceof CPPASTFunctionCallExpression)//函数调用，比如tan
		 {
			CPPASTFunctionCallExpression tmp = (CPPASTFunctionCallExpression)iastExpression;
			System.out.println(iastExpression.getRawSignature()+" Is An CPPAST Function " 
			+"   FunctionName: "+tmp.getFunctionNameExpression().getRawSignature() 
			+"   ParameterList: "+tmp.getParameterExpression().getRawSignature()
			+ tmp.getParameterExpression().getChildren().length);
			if(tmp.getFunctionNameExpression().getRawSignature().equals("pow"))
			{
				System.out.println("Pow Function Info :"+tmp.getParameterExpression().getChildren().length);
				for(IASTNode kid :  tmp.getParameterExpression().getChildren())
				{
					System.out.println(kid.getRawSignature());
				}
			}
			
			printConstraintInfo(tmp.getParameterExpression());
			
		}else if(iastExpression instanceof CPPASTExpressionList)
		{
			CPPASTExpressionList tmp = (CPPASTExpressionList)iastExpression;
			System.out.println(tmp.getRawSignature()+"   is A Parameter List");
			IASTExpression[] chi = tmp.getExpressions();
			for(IASTExpression i : chi)
				printConstraintInfo(i);
		}
		else 
		{
			//
			System.out.println(iastExpression.getRawSignature()+" Is Not An Expression");
			IASTBinaryExpression tmp = (IASTBinaryExpression)iastExpression;
		}
	}


	public List<BinaryExpression> getTarget() {
		return target;
	}


	public void setTarget(List<BinaryExpression> target) {
		this.target = target;
	}


	public Map<String, List<Interval>> getAllVarInterval() {
		return allVarInterval;
	}


	public void setAllVarInterval(Map<String, List<Interval>> allVarInterval) {
		this.allVarInterval = allVarInterval;
	}


	public Map<String, Set<String>> getAllOp() {
		return allOp;
	}


	public void setAllOp(Map<String, Set<String>> allOp) {
		this.allOp = allOp;
	}
	
	
}

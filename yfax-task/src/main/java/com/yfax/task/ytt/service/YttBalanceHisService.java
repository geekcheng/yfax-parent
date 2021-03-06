package com.yfax.task.ytt.service;

import java.text.DecimalFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yfax.task.GlobalUtils;
import com.yfax.task.ytt.dao.YttAppUserDao;
import com.yfax.task.ytt.dao.YttAwardHisDao;
import com.yfax.task.ytt.dao.YttBalanceHisDao;
import com.yfax.task.ytt.dao.YttRateSetDao;
import com.yfax.task.ytt.vo.YttAppUserVo;
import com.yfax.task.ytt.vo.YttAwardHisVo;
import com.yfax.task.ytt.vo.YttBalanceHisVo;
import com.yfax.task.ytt.vo.YttRateSetVo;
import com.yfax.utils.DateUtil;
import com.yfax.utils.JsonResult;
import com.yfax.utils.ResultCode;
import com.yfax.utils.UUID;

/**
 * 用户零钱兑换记录
 * @author Minbo
 */
@Service
public class YttBalanceHisService{
	
	protected static Logger logger = LoggerFactory.getLogger(YttBalanceHisService.class);
	
	@Autowired
	private YttBalanceHisDao dao;
	@Autowired
	private YttAppUserDao appUserDao;
	@Autowired
	private YttRateSetDao rateSetDao;
	@Autowired 
	private YttAwardHisDao awardHisDao;
	
	private static String ACTION_NAME = "零钱自动兑换";
	
	@Transactional
	public JsonResult addBalanceHis(String phoneNum, String gold){
		try {
			DecimalFormat dFormat = new DecimalFormat(GlobalUtils.DECIMAL_FORMAT);
			//得到当前汇率
			YttRateSetVo rateSetVo = this.rateSetDao.selectRateSet();
			//1. 记录零钱兑换记录
			YttBalanceHisVo balanceHisVo = new YttBalanceHisVo();
			balanceHisVo.setId(UUID.getUUID());
			balanceHisVo.setPhoneNum(phoneNum);
			balanceHisVo.setBalanceType(GlobalUtils.BALANCE_TYPE_REDEEM);
			balanceHisVo.setBalanceName(ACTION_NAME);
			balanceHisVo.setGold(gold);
			//2. 需要根据汇率计算，获得零钱
			double rate = Double.valueOf(rateSetVo.getRate());
			double goldDb = Double.valueOf(gold);
			double balanceDb = goldDb * rate;
			balanceHisVo.setBalance("+" + dFormat.format(balanceDb));
			balanceHisVo.setRate(rateSetVo.getRate());
			logger.info("当前汇率rate=" + rateSetVo.getRate() + ", 兑换金币gold=" + goldDb + ", 获得零钱balance=" + balanceHisVo.getBalance());
			String cTime = DateUtil.getCurrentLongDateTime();
			balanceHisVo.setCreateDate(cTime);
			balanceHisVo.setUpdateDate(cTime);
			//3. 扣减个人金币余额，更新用户金币余额
			YttAppUserVo appUserVo = this.appUserDao.selectByPhoneNum(phoneNum);
			//更新数据
			double balance = Double.valueOf(appUserVo.getBalance());	//原已有余额
			int old = Integer.valueOf(appUserVo.getGold());
			int sum = Integer.valueOf(appUserVo.getGold()) - Integer.valueOf(gold);
			appUserVo.setGold(String.valueOf(sum));
			appUserVo.setUpdateDate(cTime);
			appUserVo.setBalance(dFormat.format(balanceDb + balance));
			logger.info("手机号码phoneNum=" + phoneNum + ", 原金币余额gold=" + old + ", 扣减金币gold=" + gold 
				+ ", 更新后金币总余额sum=" + sum + ", 原零钱余额balance=" + balance 
				+ ", 更新后零钱余额balance=" + appUserVo.getBalance());
			//4. 记录零钱扣减记录
			YttAwardHisVo awardHisVo = new YttAwardHisVo();
			awardHisVo.setId(UUID.getUUID());
			awardHisVo.setPhoneNum(phoneNum);
			awardHisVo.setAwardType(8);
			awardHisVo.setAwardName(ACTION_NAME);
			awardHisVo.setGold("-" + String.valueOf(gold));
			awardHisVo.setCreateDate(cTime);
			awardHisVo.setUpdateDate(cTime);
			boolean flag = this.dao.insertBalanceHis(balanceHisVo);
			boolean flag1 = this.appUserDao.updateUser(appUserVo);
			boolean flag2 = this.awardHisDao.insertAwardHis(awardHisVo);
			if(flag && flag1 && flag2) {
				logger.info("转换成功。phoneNum=" + phoneNum + ", gold=" + gold);
				return new JsonResult(ResultCode.SUCCESS);
			}else {
				logger.error("转换失败。phoneNum=" + phoneNum + ", gold=" + gold 
						+ ", flag=" + flag 
						+ ", flag1=" + flag1 
						+ ", flag2=" + flag2);
				return new JsonResult(ResultCode.SUCCESS_FAIL);
			}
		} catch (Exception e) {
			logger.error("记录用户零钱兑换异常：" + e.getMessage(), e);
			return new JsonResult(ResultCode.EXCEPTION);
		}
	}
	
	public List<YttBalanceHisVo> selectBalanceHisByPhoneNum(String phoneNum) {
		return this.dao.selectBalanceHisByPhoneNum(phoneNum);
	}
	
}

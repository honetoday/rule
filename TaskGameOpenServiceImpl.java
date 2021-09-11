package com.chy.common.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chy.common.Constants;
import com.chy.common.entity.*;
import com.chy.common.mapper.*;
import com.chy.common.open.DsnFixUrlCode;
import com.chy.common.open.OpenUtil;
import com.chy.common.response.Response;
import com.chy.common.service.DsnService;
import com.chy.common.service.LhcZodiacService;
import com.chy.common.service.RedisService;
import com.chy.common.service.TaskGameOpenService;
import com.chy.common.util.ObjectUtil;
import com.chy.common.util.PropertiesUtil;
import com.chy.mq.MQSender;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

import static java.util.Collections.shuffle;


@Service
public class TaskGameOpenServiceImpl implements TaskGameOpenService {

    private static Logger logger = LoggerFactory.getLogger(TaskGameOpenServiceImpl.class);

    private static final Set<String> tenTemp = new HashSet<>(Arrays.asList(new String[]{"10","1","2","3","4","5","6","7","8","9"}));

    private static final Set<Integer> tenConfirm = new HashSet<>(Arrays.asList(new Integer[]{10,1,2,3,4,5,6,7,8,9}));

    private static final Set<Integer> fiveConfirm = new HashSet<>(Arrays.asList(new Integer[]{0,1,2,3,4,5,6,7,8,9}));

    private static final Set<String> fiveThreeTemp = new HashSet<>(Arrays.asList(new String[]{"杂六","豹子","顺子","对子","半顺"}));

    private static final Integer[] numSingle = new Integer[]{1,3,5,7,9};
    private static final Integer[] tenDouble = new Integer[]{2,4,6,8,10};
    private static final Integer[] fiveDouble = new Integer[]{2,4,6,8,0};

    private static final Integer[] gySingle = new Integer[]{3,5,7,9,11,13,15,17,19};
    private static final Integer[] gyDouble = new Integer[]{4,6,8,10,12,14,16,18};
    private static final List<Integer> tenGYH = Arrays.asList(10, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    private static final Set<String> tenString = new HashSet<>(Arrays.asList(new String[]{"10","1","2","3","4","5","6","7","8","9"}));

    private static final List<Integer> fiveZH = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    private static final Set<String> fiveString = new HashSet<>(Arrays.asList(new String[]{"0","1","2","3","4","5","6","7","8","9"}));
    private static final Integer[] fiveHeSingle = new Integer[]{1,3,5,7,9,11,13,15,17,19,21,23,25,27,29,31,33,35,37,39,41,43,45};
    private static final Integer[] fiveHeDouble = new Integer[]{0,2,4,6,8,10,12,14,16,18,20,22,24,26,28,30,32,34,36,38,40,42,44};

    private static final Set<String> threeString = new HashSet<>(Arrays.asList(new String[]{"0","1","2","3","4","5","6","7","8","9","10",
            "11","12","13","14","15","16","17","18","19","20","21","22","23","24","25","26","27"}));
    private static final int[] threeNum = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    private static final Integer[] threeSingle = new Integer[]{1,3,5,7,9,11,13,15,17,19,21,23,25,27};
    private static final Integer[] threeDouble = new Integer[]{0,2,4,6,8,10,12,14,16,18,20,22,24,26};
    private static final Integer[] threeDaDan = new Integer[]{15,17,19,21,23,25,27};
    private static final Integer[] threeXiaoDan = new Integer[]{1,3,5,7,9,11,13};
    private static final Integer[] threeDaShuang = new Integer[]{14,16,18,20,22,24,26};
    private static final Integer[] threeXiaoShuang = new Integer[]{0,2,4,6,8,10,12};
    private static final Integer[] lvBo = new Integer[]{1,4,7,10,16,19,22,25};
    private static final Integer[] lanBo = new Integer[]{2,5,8,11,17,20,23,26};
    private static final Integer[] hongBo = new Integer[]{3,6,9,12,15,18,21,24};


    @Autowired
    private TaskGameMapper taskGameMapper;

    @Autowired
    private OpenLogMapper openLogMapper;

    @Autowired
    private TaskGameOpenTimeMapper taskGameOpenTimeMapper;

    @Autowired
    private RedisService redisService;

    @Autowired
    private TaskGameSubOrderMapper taskGameOrderMapper;

    @Autowired
    private MQSender mqSender;

    @Autowired
    private DsnService dsnService;

    @Autowired
    private GameFixedPeriodMapper gameFixedPeriodMapper;

    @Autowired
    private LhcZodiacService lhcZodiacService;

    private static final String dsnHost = PropertiesUtil.getValue("dsn.url");

    @Override
    public void openDraw(Integer gameId,Long period) throws JsonProcessingException {
        Game game = taskGameMapper.selectByPrimaryKey(gameId);
        openLottery(game,period);
    }

    @Override
    public void openFiveDraw(int gameId, Long period) throws JsonProcessingException {
        Game game = taskGameMapper.selectByPrimaryKey(gameId);
        openLottery(game,period);
    }

    @Override
    public void openThreeDraw(int gameId, Long period) throws JsonProcessingException {
        Game game = taskGameMapper.selectByPrimaryKey(gameId);
        openLottery(game,period);
    }

    private void openLottery(Game game, Long period) throws JsonProcessingException {
        OpenLog openLog = new OpenLog();
        openLog.setGameId(game.getId());
        openLog.setGameName(game.getName());
        GameOpenTime openTime;
        if(null != period){
            //固定开奖
            openTime = taskGameOpenTimeMapper.getOpenTime(game.getId(),period);
        }else{
            openTime = taskGameOpenTimeMapper.getPeriod(game.getId());
        }

        openLog.setPeriod(openTime.getPeriod());
        openLog.setOpenTime(openTime.getEndTime());
        openLog.setDrawPeriod(openTime.getPeriod() + 1);
        openLog.setDrawTime(openTime.getNextTime());

        //TODO 改完开奖打开
        String openPeriod;
        switch (game.getOpenType()) {
            case 2:
                //设定金额开
                openPeriod = fixedAmountOpen(game.getFixedAmount(),game.getOpenNum(),openLog);
                break;
            case 3:
                //混合开
                openPeriod = mixedModelOpen(game.getOpenNum(),openLog);
//              openPeriod = openRandomNum(game.getOpenNum());
                break;
            default:
                //随机开
                openPeriod = openRandomNum(game.getOpenNum());
                break;
        }
//        String openPeriod = openRandomNum(game.getOpenNum());
        openLogSet(game.getOpenNum(),openPeriod,openLog);
        openLogMapper.insertSelective(openLog);

        if(null == period){
            mqSender.sendObj(Constants.MQQueue.OPEN_QUEUE, openLog);
        }
    }

    //混合开
    public String mixedModelOpen(Integer num, OpenLog log) {
        Integer gameId = log.getGameId();
        Long period = log.getPeriod();
        //取随机期数
        GameFixedPeriod fixedPeriod = null;
        try {
            fixedPeriod = gameFixedPeriodMapper.getPeriod(gameId, period);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("获取随机期数出错gameId：" + gameId + "，期数：" + period,e);
        }
        String openPeriod = null;
        if(null == fixedPeriod){
            logger.info("按最少开：gameId：" + gameId + "，期数：" + period);
            //按最少开
            Map<String, Long> totalUpOrder = getTotalUpOrder(gameId, period,num);
            if(null != totalUpOrder && totalUpOrder.size() > 0){
                //TODO:测试代码
//                if(num == 3){ return openRandomNum(3);}
//                if(num == 5){ return openRandomNum(5);}
                Integer[] kc = getKC(num, log, totalUpOrder, 0L);

                StringBuilder builder = new StringBuilder();
                List<Integer> end = Arrays.asList(kc);
                for(int i = 0; i< num; i++){
                    if(null == kc[i]){
                        if(num == 10){
                            for (Integer s : tenConfirm) {
                                if(!end.contains(s)){
                                    kc[i] = s;
                                }
                            }
                        }else if(num == 5 || num == 3){
                            for (Integer s : fiveConfirm) {
                                if(!end.contains(s)){
                                    kc[i] = s;
                                }
                            }
                        }
                    }
                    builder.append(kc[i] + ",");
                }

                openPeriod = builder.substring(0, builder.length() - 1);
                if(num == 5 || num == 3){
                    openPeriod = openPeriod.replaceAll("10","0");
                }
                totalUpOrder.clear();
                return openPeriod;
            }
        }
        if(null == openPeriod){
            logger.info("随机开：gameId：" + gameId + "，期数：" + period);
            openPeriod = openRandomNum(num);
        }
        return openPeriod;
    }


    //设定金额开
    public String fixedAmountOpen(Long fixedAmount, Integer num, OpenLog log) {
        //TODO:测试代码
//        if(num == 3){ return openRandomNum(3);}
//        if(num == 5){ return openRandomNum(5);}
        Map<String, Long> totalUpOrder = getTotalUpOrder(log.getGameId(), log.getPeriod(),num);
        if(totalUpOrder.size() > 0){

            //数字已买
            Integer[] kc = getKC(num, log, totalUpOrder, fixedAmount);

            StringBuilder builder = new StringBuilder();
            List<Integer> end = Arrays.asList(kc);
            for(int i = 0; i< num; i++){
                if(null == kc[i]){
                    if(num == 10){
                        for (Integer s : tenConfirm) {
                            if(!end.contains(s)){
                                kc[i] = s;
                            }
                        }
                    }else if(num == 5 || num == 3){
                        for (Integer s : fiveConfirm) {
                            if(!end.contains(s)){
                                kc[i] = s;
                            }
                        }
                    }
                }
                builder.append(kc[i] + ",");
            }

            String openPeriod = builder.substring(0, builder.length() - 1);
            if(num == 5 || num == 3){
                openPeriod = openPeriod.replaceAll("10","0");
            }
            totalUpOrder.clear();
            return openPeriod;
        }
        else{
            //无上盘，随机开
            return openRandomNum(num);
        }
    }

    private Integer[] getKC(Integer num, OpenLog log, Map<String, Long> totalUpOrder, long fixedAmount) {
        //数字已买
        Map<Integer, Set<String>> numberBet = new HashMap<>();

        //前三 中三 后三
        Set<String> behindBet = new HashSet<>();
        Set<String> betweenBet = new HashSet<>();
        Set<String> lastBet = new HashSet<>();

        if (num != 3) {
            numberBet(fixedAmount, num, totalUpOrder, numberBet);
            if (num == 5) {
                for (String key : totalUpOrder.keySet()) {
                    Long betAmount = totalUpOrder.get(key);
                    String betValue = key.split(":")[1];
                    if (betAmount > fixedAmount) {
                        //总和已买
                        if (key.contains(Constants.GameRule.QIAN_SAN + ":")) {
                            behindBet.add(betValue);

                        } else if (key.contains(Constants.GameRule.ZHONG_SAN + ":")) {
                            betweenBet.add(betValue);

                        } else if (key.contains(Constants.GameRule.HOU_SAN + ":")) {
                            lastBet.add(betValue);
                        }
                    }
                }
            }
        }

        //可开号码
        Map<Integer, Set<String>> numKeKai = new HashMap<>();

        Set<String> behindKeKai = new HashSet<>();
        Set<String> betweenKeKai = new HashSet<>();
        Set<String> lastKeKai = new HashSet<>();


        //开出号码
        Integer[] kc = new Integer[num];
        //根据全部的玩法比较已买 得出未买的
        if (num != 3) {
            numberKekaiSet(num, numberBet, numKeKai);
        }

        if (num == 10) {
            //tenTemp
            List<Map<Integer, List<String>>> numList = new ArrayList<>();
            //开出号码
            Set<String> one = numKeKai.get(1);
            List<String> oneList = new ArrayList<>(one);
            shuffle(oneList);
            Map<Integer, List<String>> map1 = new HashMap<>();
            map1.put(0, oneList);
            numList.add(map1);

            Set<String> two = numKeKai.get(2);
            List<String> twoList = new ArrayList<>(two);
            shuffle(twoList);
            Map<Integer, List<String>> map2 = new HashMap<>();
            map2.put(1, twoList);
            numList.add(map2);

            Set<String> three = numKeKai.get(3);
            List<String> threeList = new ArrayList<>(three);
            shuffle(threeList);
            Map<Integer, List<String>> map3 = new HashMap<>();
            map3.put(2, threeList);
            numList.add(map3);


            Set<String> four = numKeKai.get(4);
            List<String> fourList = new ArrayList<>(four);
            shuffle(fourList);
            Map<Integer, List<String>> map4 = new HashMap<>();
            map4.put(3, fourList);
            numList.add(map4);


            Set<String> five = numKeKai.get(5);
            List<String> fiveList = new ArrayList<>(five);
            shuffle(fiveList);
            Map<Integer, List<String>> map5 = new HashMap<>();
            map5.put(4, fiveList);
            numList.add(map5);


            Set<String> six = numKeKai.get(6);
            List<String> sixList = new ArrayList<>(six);
            shuffle(sixList);
            Map<Integer, List<String>> map6 = new HashMap<>();
            map6.put(5, sixList);
            numList.add(map6);


            Set<String> seven = numKeKai.get(7);
            List<String> sevenList = new ArrayList<>(seven);
            shuffle(sevenList);
            Map<Integer, List<String>> map7 = new HashMap<>();
            map7.put(6, sevenList);
            numList.add(map7);


            Set<String> eight = numKeKai.get(8);
            List<String> eightList = new ArrayList<>(eight);
            shuffle(eightList);
            Map<Integer, List<String>> map8 = new HashMap<>();
            map8.put(7, eightList);
            numList.add(map8);


            Set<String> nine = numKeKai.get(9);
            List<String> nineList = new ArrayList<>(nine);
            shuffle(nineList);
            Map<Integer, List<String>> map9 = new HashMap<>();
            map9.put(8, nineList);
            numList.add(map9);


            Set<String> ten = numKeKai.get(0);
            List<String> tenList = new ArrayList<>(ten);
            shuffle(tenList);
            Map<Integer, List<String>> map10 = new HashMap<>();
            map10.put(9, tenList);
            numList.add(map10);



            Comparator<Map<Integer, List<String>>> stringLengthComparator = new Comparator<Map<Integer, List<String>>>() {
                @Override
                public int compare(Map<Integer, List<String>> o1, Map<Integer, List<String>> o2) {
                    Iterator<Integer> iterator1 = o1.keySet().iterator();
                    Integer next1 = iterator1.next();
                    Iterator<Integer> iterator2 = o2.keySet().iterator();
                    Integer next2 = iterator2.next();
                    return Integer.compare(o1.get(next1).size(), o2.get(next2).size());
                }
            };

            Collections.sort(numList, stringLengthComparator);


            for (Map<Integer, List<String>> kai : numList) {
                for (Iterator<Integer> iterator = kai.keySet().iterator(); iterator.hasNext(); ) {
                    Integer key = iterator.next();
                    kaiNum(kc, kai.get(key), key, numberBet, totalUpOrder);
                }
            }

            List<Integer> yk = Arrays.asList(kc);
            for (int i = 0; i < kc.length; i++) {
                if (null == kc[i]) {
                    Set<String> set = numberBet.get(i);
                    for (String s : set) {
                        if (ObjectUtil.isInteger(s) && !yk.contains(Integer.valueOf(s))) {
                            kc[i] = Integer.valueOf(s);
                            break;
                        }
                    }
                }
            }
            map10.clear();
            ten.clear();
            tenList.clear();
            map9.clear();
            nine.clear();
            nineList.clear();
            map8.clear();
            eight.clear();
            eightList.clear();
            map7.clear();
            seven.clear();
            sevenList.clear();
            map6.clear();
            six.clear();
            sixList.clear();
            map5.clear();
            five.clear();
            fiveList.clear();
            map4.clear();
            four.clear();
            fourList.clear();
            map3.clear();
            three.clear();
            threeList.clear();
            map2.clear();
            two.clear();
            twoList.clear();
            map1.clear();
            one.clear();
            oneList.clear();
            numList.clear();
            //============================================
        }
        else if (num == 5) {

            if (behindBet.size() > 0) {
                getKeKai(behindBet, behindKeKai, fiveThreeTemp);
            } else {
                behindKeKai.addAll(fiveThreeTemp);
            }
            List<String> behindList = new ArrayList<>(behindKeKai);
            shuffle(behindList);

            if (betweenBet.size() > 0) {
                getKeKai(betweenBet, betweenKeKai, fiveThreeTemp);
            } else {
                betweenKeKai.addAll(fiveThreeTemp);
            }
            List<String> betweenList = new ArrayList<>(betweenKeKai);
            shuffle(betweenList);

            if (lastBet.size() > 0) {
                getKeKai(lastBet, lastKeKai, fiveThreeTemp);
            } else {
                lastKeKai.addAll(fiveThreeTemp);
            }
            List<String> lastList = new ArrayList<>(lastKeKai);
            shuffle(lastList);

            //开出号码
            Set<String> one = numKeKai.get(0);
            List<String> oneList = new ArrayList<>(one);
            shuffle(oneList);

            Set<String> two = numKeKai.get(1);
            List<String> twoList = new ArrayList<>(two);
            shuffle(twoList);

            Set<String> three = numKeKai.get(2);
            List<String> threeList = new ArrayList<>(three);
            shuffle(threeList);

            Set<String> four = numKeKai.get(3);
            List<String> fourList = new ArrayList<>(four);
            shuffle(fourList);

            Set<String> five = numKeKai.get(4);
            List<String> fiveList = new ArrayList<>(five);
            shuffle(fiveList);


            for (String oneNum : oneList) {
                kc[0] = Integer.valueOf(oneNum);
                break;
            }

            if (null == kc[0]) {
                for (String key : totalUpOrder.keySet()) {
                    if (key.contains(1 + ":")) {
                        String s = key.split(":")[1];
                        if (ObjectUtil.isInteger(s)) {
                            kc[0] = Integer.valueOf(s);
                            break;
                        }
                    }
                }
            }

            for (String twoNum : twoList) {
                Integer second = Integer.valueOf(twoNum);
                for (String behind : behindList) {
                    if (kc[0].equals(second) && behind.contains(Constants.Rule.DUI_ZI)) {
                        kc[1] = second;
                        break;
                    } else if ((kc[0] + 1 == second || second + 1 == kc[0]) && behind.contains(Constants.Rule.BAN_SHUN)) {
                        kc[1] = second;
                        break;
                    } else if ((!kc[0].equals(second)) && (kc[0] + 1 != second || second + 1 != kc[0]) && behind.contains(Constants.Rule.ZA_LIU)) {
                        kc[1] = second;
                        break;
                    }
                }
                if (null != kc[1]) {
                    break;
                }
            }

            if (null == kc[1]) {
                for (String key : totalUpOrder.keySet()) {
                    String s = key.split(":")[1];
                    if (key.contains(2 + ":")) {
                        if (ObjectUtil.isInteger(s)) {
                            Integer second = Integer.valueOf(s);
                            for (String behind : behindList) {
                                if (kc[0].equals(second) && behind.contains(Constants.Rule.DUI_ZI)) {
                                    kc[1] = second;
                                    break;
                                } else if ((kc[0] + 1 == second || second + 1 == kc[0]) && behind.contains(Constants.Rule.BAN_SHUN)) {
                                    kc[1] = second;
                                    break;
                                } else if ((!kc[0].equals(second)) && (kc[0] + 1 != second || second + 1 != kc[0]) && behind.contains(Constants.Rule.ZA_LIU)) {
                                    kc[1] = second;
                                    break;
                                }
                            }
                            if (null != kc[1]) {
                                break;
                            }
                        }
                    }
                }
                if (null == kc[1]) {
                    for (String key : totalUpOrder.keySet()) {
                        if (key.contains(2 + ":")) {
                            kc[1] = Integer.valueOf(key.split(":")[1]);
                            break;
                        }
                    }
                }
            }

            for (String threeNum : threeList) {
                Integer thr = Integer.valueOf(threeNum);
                String checkThreeNums = OpenUtil.checkThreeNums(kc[0], kc[1], thr);
                for (String be : behindList) {
                    if (checkThreeNums.equals(Constants.Rule.BAN_SHUN)
                            && be.contains(Constants.Rule.BAN_SHUN)) {
                        kc[2] = thr;
                        break;
                    } else if (checkThreeNums.equals(Constants.Rule.ZA_LIU)
                            && be.contains(Constants.Rule.ZA_LIU)) {
                        kc[2] = thr;
                        break;
                    } else if (checkThreeNums.equals(Constants.Rule.DUI_ZI)
                            && be.contains(Constants.Rule.DUI_ZI)) {
                        kc[2] = thr;
                        break;
                    } else if (checkThreeNums.equals(Constants.Rule.SHUN_ZI)
                            && be.contains(Constants.Rule.SHUN_ZI)) {
                        kc[2] = thr;
                        break;
                    } else if (checkThreeNums.equals(Constants.Rule.BAO_ZI)
                            && be.contains(Constants.Rule.BAO_ZI)) {
                        String kaiBao = redisService.get(Constants.RedisKeys.BAO_ZI_KAI + log.getGameId());
                        if (!StringUtils.isEmpty(kaiBao) && "1".equals(kaiBao)) {
                            kc[2] = thr;
                            break;
                        }
                        continue;
                    }
                }
                if (null != kc[2]) {
                    break;
                }
            }
            if (null == kc[2]) {
                for (String key : totalUpOrder.keySet()) {
                    String s = key.split(":")[1];
                    if (key.contains(3 + ":")) {
                        if (ObjectUtil.isInteger(s)) {
                            Integer thr = Integer.valueOf(s);
                            String checkThreeNums = OpenUtil.checkThreeNums(kc[0], kc[1], thr);
                            for (String be : behindList) {
                                if (checkThreeNums.equals(Constants.Rule.BAN_SHUN)
                                        && be.contains(Constants.Rule.BAN_SHUN)) {
                                    kc[2] = thr;
                                    break;
                                } else if (checkThreeNums.equals(Constants.Rule.ZA_LIU)
                                        && be.contains(Constants.Rule.ZA_LIU)) {
                                    kc[2] = thr;
                                    break;
                                } else if (checkThreeNums.equals(Constants.Rule.DUI_ZI)
                                        && be.contains(Constants.Rule.DUI_ZI)) {
                                    kc[2] = thr;
                                    break;
                                } else if (checkThreeNums.equals(Constants.Rule.SHUN_ZI)
                                        && be.contains(Constants.Rule.SHUN_ZI)) {
                                    kc[2] = thr;
                                    break;
                                } else if (checkThreeNums.equals(Constants.Rule.BAO_ZI)
                                        && be.contains(Constants.Rule.BAO_ZI)) {
                                    String kaiBao = redisService.get(Constants.RedisKeys.BAO_ZI_KAI + log.getGameId());
                                    if (!StringUtils.isEmpty(kaiBao) && "1".equals(kaiBao)) {
                                        kc[2] = thr;
                                        break;
                                    }
                                    continue;
                                }
                            }
                            if (null != kc[2]) {
                                break;
                            }
                        }
                    } else if (key.contains("前三:")) {
                        for (String threeNum : threeList) {
                            Integer thr = Integer.valueOf(threeNum);
                            String checkThreeNums = OpenUtil.checkThreeNums(kc[0], kc[1], thr);
                            if (checkThreeNums.equals(s)) {
                                kc[2] = thr;
                                break;
                            }
                        }
                        if (null != kc[2]) {
                            break;
                        }
                        for (String keyNum : totalUpOrder.keySet()) {
                            if (keyNum.contains(3 + ":")) {
                                String sn = keyNum.split(":")[1];
                                if (ObjectUtil.isInteger(sn)) {
                                    Integer thr = Integer.valueOf(sn);
                                    String checkThreeNums = OpenUtil.checkThreeNums(kc[0], kc[1], thr);
                                    if (checkThreeNums.equals(s)) {
                                        kc[2] = thr;
                                        break;
                                    }
                                }
                            }
                        }
                        if (null != kc[2]) {
                            break;
                        }
                    }
                }

                if (null == kc[2]) {
                    for (String key : totalUpOrder.keySet()) {
                        if (key.contains(3 + ":")) {
                            kc[2] = Integer.valueOf(key.split(":")[1]);
                            break;
                        }
                    }
                }
            }


            for (String fourNum : fourList) {
                Integer fou = Integer.valueOf(fourNum);
                String checkThreeNums = OpenUtil.checkThreeNums(kc[1], kc[2], fou);

                for (String bt : betweenKeKai) {
                    if (checkThreeNums.equals(Constants.Rule.SHUN_ZI)
                            && bt.contains(Constants.Rule.SHUN_ZI)) {
                        kc[3] = fou;
                        break;
                    } else if (checkThreeNums.equals(Constants.Rule.DUI_ZI)
                            && bt.contains(Constants.Rule.DUI_ZI)) {
                        kc[3] = fou;
                        break;
                    } else if (checkThreeNums.equals(Constants.Rule.ZA_LIU)
                            && bt.contains(Constants.Rule.ZA_LIU)) {
                        kc[3] = fou;
                        break;
                    } else if (checkThreeNums.equals(Constants.Rule.BAN_SHUN)
                            && bt.contains(Constants.Rule.BAN_SHUN)) {
                        kc[3] = fou;
                        break;
                    } else if (checkThreeNums.equals(Constants.Rule.BAO_ZI)
                            && bt.contains(Constants.Rule.BAO_ZI)) {
                        String kaiBao = redisService.get(Constants.RedisKeys.BAO_ZI_KAI + log.getGameId());
                        if (!StringUtils.isEmpty(kaiBao) && "1".equals(kaiBao)) {
                            kc[3] = fou;
                            break;
                        }
                        continue;
                    }
                }
                if (null != kc[3]) {
                    break;
                }
            }
            if (null == kc[3]) {
                for (String key : totalUpOrder.keySet()) {
                    String s = key.split(":")[1];
                    if (key.contains(4 + ":")) {
                        if (ObjectUtil.isInteger(s)) {
                            Integer fou = Integer.valueOf(s);
                            String checkThreeNums = OpenUtil.checkThreeNums(kc[1], kc[2], fou);

                            for (String bt : betweenKeKai) {
                                if (checkThreeNums.equals(Constants.Rule.SHUN_ZI)
                                        && bt.contains(Constants.Rule.SHUN_ZI)) {
                                    kc[3] = fou;
                                    break;
                                } else if (checkThreeNums.equals(Constants.Rule.DUI_ZI)
                                        && bt.contains(Constants.Rule.DUI_ZI)) {
                                    kc[3] = fou;
                                    break;
                                } else if (checkThreeNums.equals(Constants.Rule.ZA_LIU)
                                        && bt.contains(Constants.Rule.ZA_LIU)) {
                                    kc[3] = fou;
                                    break;
                                } else if (checkThreeNums.equals(Constants.Rule.BAN_SHUN)
                                        && bt.contains(Constants.Rule.BAN_SHUN)) {
                                    kc[3] = fou;
                                    break;
                                } else if (checkThreeNums.equals(Constants.Rule.BAO_ZI)
                                        && bt.contains(Constants.Rule.BAO_ZI)) {
                                    String kaiBao = redisService.get(Constants.RedisKeys.BAO_ZI_KAI + log.getGameId());
                                    if (!StringUtils.isEmpty(kaiBao) && "1".equals(kaiBao)) {
                                        kc[3] = fou;
                                        break;
                                    }
                                    continue;
                                }
                            }
                            if (null != kc[3]) {
                                break;
                            }
                        }
                    } else if (key.contains("中三:")) {
                        for (String fouNum : fourList) {
                            Integer fou = Integer.valueOf(fouNum);
                            String checkThreeNums = OpenUtil.checkThreeNums(kc[1], kc[2], fou);
                            if (checkThreeNums.equals(s)) {
                                kc[3] = fou;
                                break;
                            }
                        }
                        if (null != kc[3]) {
                            break;
                        }
                        for (String keyNum : totalUpOrder.keySet()) {
                            if (keyNum.contains(4 + ":")) {
                                String sn = keyNum.split(":")[1];
                                if (ObjectUtil.isInteger(sn)) {
                                    Integer fou = Integer.valueOf(sn);
                                    String checkThreeNums = OpenUtil.checkThreeNums(kc[1], kc[2], fou);
                                    if (checkThreeNums.equals(s)) {
                                        kc[3] = fou;
                                        break;
                                    }
                                }
                            }
                        }
                        if (null != kc[3]) {
                            break;
                        }
                    }
                }
                if (null == kc[3]) {
                    for (String key : totalUpOrder.keySet()) {
                        if (key.contains(4 + ":")) {
                            kc[3] = Integer.valueOf(key.split(":")[1]);
                            break;
                        }
                    }
                }
            }

            for (String fiveNum : fiveList) {
                Integer fiv = Integer.valueOf(fiveNum);
                String checkThreeNums = OpenUtil.checkThreeNums(kc[2], kc[3], fiv);
                for (String ls : lastList) {
                    if (checkThreeNums.equals(Constants.Rule.ZA_LIU)
                            && ls.contains(Constants.Rule.ZA_LIU)) {
                        kc[4] = fiv;
                        break;
                    } else if (checkThreeNums.equals(Constants.Rule.BAN_SHUN)
                            && ls.contains(Constants.Rule.BAN_SHUN)) {
                        kc[4] = fiv;
                        break;
                    } else if (checkThreeNums.equals(Constants.Rule.SHUN_ZI)
                            && ls.contains(Constants.Rule.SHUN_ZI)) {
                        kc[4] = fiv;
                        break;
                    } else if (checkThreeNums.equals(Constants.Rule.DUI_ZI)
                            && ls.contains(Constants.Rule.DUI_ZI)) {
                        kc[4] = fiv;
                        break;
                    } else if (checkThreeNums.equals(Constants.Rule.BAO_ZI)
                            && ls.contains(Constants.Rule.BAO_ZI)) {
                        String kaiBao = redisService.get(Constants.RedisKeys.BAO_ZI_KAI + log.getGameId());
                        if (!StringUtils.isEmpty(kaiBao) && "1".equals(kaiBao)) {
                            kc[4] = fiv;
                            break;
                        }
                        continue;
                    }
                }
                if (null != kc[4]) {
                    break;
                }
            }

            if (null == kc[4]) {
                for (String key : totalUpOrder.keySet()) {
                    String s = key.split(":")[1];
                    if (key.contains(5 + ":")) {
                        if (ObjectUtil.isInteger(s)) {
                            Integer fiv = Integer.valueOf(s);
                            String checkThreeNums = OpenUtil.checkThreeNums(kc[2], kc[3], fiv);
                            for (String ls : lastList) {
                                if (checkThreeNums.equals(Constants.Rule.ZA_LIU)
                                        && ls.contains(Constants.Rule.ZA_LIU)) {
                                    kc[4] = fiv;
                                    break;
                                } else if (checkThreeNums.equals(Constants.Rule.BAN_SHUN)
                                        && ls.contains(Constants.Rule.BAN_SHUN)) {
                                    kc[4] = fiv;
                                    break;
                                } else if (checkThreeNums.equals(Constants.Rule.SHUN_ZI)
                                        && ls.contains(Constants.Rule.SHUN_ZI)) {
                                    kc[4] = fiv;
                                    break;
                                } else if (checkThreeNums.equals(Constants.Rule.DUI_ZI)
                                        && ls.contains(Constants.Rule.DUI_ZI)) {
                                    kc[4] = fiv;
                                    break;
                                } else if (checkThreeNums.equals(Constants.Rule.BAO_ZI)
                                        && ls.contains(Constants.Rule.BAO_ZI)) {
                                    String kaiBao = redisService.get(Constants.RedisKeys.BAO_ZI_KAI + log.getGameId());
                                    if (!StringUtils.isEmpty(kaiBao) && "1".equals(kaiBao)) {
                                        kc[4] = fiv;
                                        break;
                                    }
                                    continue;
                                }
                            }
                            if (null != kc[4]) {
                                break;
                            }
                        }
                    } else if (key.contains("后三:")) {
                        for (String fivNum : fiveList) {
                            Integer fiv = Integer.valueOf(fivNum);
                            String checkThreeNums = OpenUtil.checkThreeNums(kc[2], kc[3], fiv);
                            if (checkThreeNums.equals(s)) {
                                kc[4] = fiv;
                                break;
                            }
                        }
                        if (null != kc[4]) {
                            break;
                        }
                        for (String keyNum : totalUpOrder.keySet()) {
                            if (keyNum.contains(5 + ":")) {
                                String sn = keyNum.split(":")[1];
                                if (ObjectUtil.isInteger(sn)) {
                                    Integer fiv = Integer.valueOf(sn);
                                    String checkThreeNums = OpenUtil.checkThreeNums(kc[2], kc[3], fiv);
                                    if (checkThreeNums.equals(s)) {
                                        kc[4] = fiv;
                                        break;
                                    }
                                }
                            }
                        }
                        if (null != kc[4]) {
                            break;
                        }
                    }
                }
                if (null == kc[4]) {
                    for (String key : totalUpOrder.keySet()) {
                        if (key.contains(5 + ":")) {
                            kc[4] = Integer.valueOf(key.split(":")[1]);
                            break;
                        }
                    }
                }
            }


//                if(kc[0] == kc[1] && kc[1] == kc[2] && kc[2] == kc[3] &&  kc[3] == kc[4]){
//                    //5连号，重开
//                    fixedAmountOpen(fixedAmount,num,log);
//                }

            behindList.clear();
            betweenList.clear();
            lastList.clear();
            one.clear();
            oneList.clear();
            two.clear();
            twoList.clear();
            three.clear();
            threeList.clear();
            four.clear();
            fourList.clear();
            five.clear();
            fiveList.clear();
            //=================================================
        }
        else if (num == 3) {
            Set<String> mai = new HashSet<>();
            int a = 0;
            List<String> minList = new LinkedList<>();
            for (String key : totalUpOrder.keySet()) {
                mai.add(key);
                if (a < 3) {
                    if (a == 0) {
                        minList.add(key);
                    } else if (totalUpOrder.get(key).equals(totalUpOrder.get(minList.get(0)))) {
                        minList.add(key);
                    }
                    a++;
                }
            }
            String he = minList.get(0);
            if (minList.size() == 2) {
                he = minList.get(RandomUtil.randomInt(0, 2));
            } else if (minList.size() == 3) {
                he = minList.get(RandomUtil.randomInt(0, 3));
            }
            Set<String> keKai = ObjectUtil.compare(threeString, mai);
            if (keKai.size() > 0) {
                List<String> keKaiList = new ArrayList<>(keKai);
                shuffle(keKaiList);
                he = keKaiList.get(keKaiList.size() / 2);
            }
            List<List<Integer>> kaiList = getThreeNums(threeNum, Integer.valueOf(he));
            shuffle(kaiList);
            List<Integer> kai = kaiList.get(kaiList.size() / 2);
            kc[0] = kai.get(0);
            kc[1] = kai.get(1);
            kc[2] = kai.get(2);
            minList.clear();
            keKai.clear();
        }

        numberBet.clear();
        behindBet.clear();
        betweenBet.clear();
        lastBet.clear();
        numKeKai.clear();
        behindKeKai.clear();
        betweenKeKai.clear();
        lastKeKai.clear();
        return kc;
    }


    private void kaiNum(Integer[] kc, List<String> numList, int index, Map<Integer, Set<String>> numberBet, Map<String, Long> totalUpOrder) {
        List<Integer> list = Arrays.asList(kc);
        Set<String> compare = ObjectUtil.compare(new HashSet<>(numList),numberBet.get(index));
        //未买
        if(compare.size() > 0){
            List<String> strings = new ArrayList<>(compare);
            shuffle(strings);
            for (String s : strings) {
                if (ObjectUtil.isInteger(s) && !list.contains(Integer.valueOf(s))) {
                    kc[index] = Integer.valueOf(s);
                    break;
                }
            }
        }

        //终极无码
        if(null == kc[index]){
            for (String key : totalUpOrder.keySet()) {
                String flag = index + ":";
                if(index == 0){
                    flag = "1:";
                }else if(index == 9){
                    flag = "0:";
                }
                 if(key.contains(flag)){
                     String[] split = key.split(":");
                     String s = split[1];
                     if("0".equals(s)) {
                         s = "10";
                     }
                     if (!list.contains(Integer.valueOf(s))) {
                         kc[index] = Integer.valueOf(s);
                         break;
                     }
                 }
            }
        }
    }

    public static List<List<Integer>> getThreeNums(int[] num,int sum) {
        List<List<Integer>> res = new ArrayList<>();
        for (int a : num) {
            if(a > sum) {
                continue;
            }
            for (int b : num) {
                if(b > sum) {
                    continue;
                }
                for (int c : num) {
                    if(c > sum) {
                        continue;
                    }
                    if((a + b + c) == sum){
                        res.add(Arrays.asList(a, b, c));
                    }
                }
            }
        }
        return res;
    }

    public static Set<Integer> getNums(List<Integer> array,int he){
        Set<String> result = new HashSet<>();
        //从1循环到2^N
        for (int i = 1; i < 1 << array.size(); i++)
        {
            int sum = 0;
            StringBuilder temp = new StringBuilder();
            for (int j = 0; j < array.size(); j++)
            {
                //用i与2^j进行位与运算，若结果不为0,则表示第j位不为0,从数组中取出第j个数
                if ((i & 1 << j) != 0)
                {
                    sum += array.get(j);
                    temp.append(array.get(j));
                }
            }
            if(sum == he){
                //如果和为所求，则输出
                result.add(temp.toString());
            }
        }
        Set<Integer> res = new HashSet<>();
        for (String s : result) {
            String[] split = s.split("");
            for (String s1 : split) {
                res.add(Integer.valueOf(s1));
            }
        }
        return res;
    }

    public static List<String> getNums(List<Integer> num, int sum,Set<String> one,Set<String> two){
        Set<Integer> set = new HashSet<>();
        for (int i = 0; i < num.size(); i++) {
            Integer val = num.get(i);
            int tmp = sum - val;
            if(num.contains(tmp) && sum != 2 * tmp){
                if(!(set.contains(val) || set.contains(tmp))){
                    set.add(val);
                    continue;
                }
            }
        }

        List<String> result = new ArrayList<>();
        for (Integer value : set) {
            String tmp1 = value + "";
            String tmp2 = (sum - value) + "";
            if(one.contains(tmp1) && two.contains(tmp2)){
                result.add(tmp1 + ":" + tmp2);
            }else if(one.contains(tmp2) && two.contains(tmp1)){
                result.add(tmp2 + ":" + tmp1);
            }
        }
        return result;
    }

    private void getKeKai(Set<String> bet, Set<String> keKai, Set<String> temp) {
        Set<String> notExits = ObjectUtil.compare(temp, bet);
        keKai.addAll(notExits);
    }

    private void numberKekaiSet(Integer num, Map<Integer, Set<String>> numberBet, Map<Integer, Set<String>> keKai) {
        Set<String> temp = TaskGameOpenServiceImpl.tenTemp;
        if(num == 5 || num == 3){
            temp = TaskGameOpenServiceImpl.fiveString;
        }
        if (numberBet.size() > 0) {
            for (int i = 0; i < num; i++) {
                Set<String> numNotExits = ObjectUtil.compare(temp, numberBet.get(i));
                keKai.put(i, numNotExits);
            }
        }else{
            for (int i = 0; i < num; i++) {
                keKai.put(i, temp);
            }
        }
    }

    private void numberBet(Long fixedAmount, Integer num, Map<String, Long> totalUpOrder,
                           Map<Integer, Set<String>> numberBet) {
        if(num == 10){
            for (int i = 0; i < num; i++) {
                Set<String> bet = new HashSet<>();
                for (String key : totalUpOrder.keySet()) {
                    Long betAmount = totalUpOrder.get(key);
                    if (betAmount > fixedAmount) {
                        if (key.contains(i + ":")) {
                            String s = key.split(":")[1];
                            if(num == 10 && "0".equals(s)){
                                s = "10";
                            }
                            bet.add(s);
                        }
                    }
                }
                numberBet.put(i, bet);
            }
        }else{
            for (int i = 1; i <= num; i++) {
                Set<String> bet = new HashSet<>();
                for (String key : totalUpOrder.keySet()) {
                    Long betAmount = totalUpOrder.get(key);
                    if (betAmount > fixedAmount) {
                        if (key.contains(i + ":")) {
                            String s = key.split(":")[1];
                            bet.add(s);
                        }
                    }
                }
                numberBet.put(i - 1, bet);
            }
        }
    }


    private Map<String,Long> getTotalUpOrder(Integer gameId,Long period,Integer num){
        List<GameSubOrder> subOrders = taskGameOrderMapper.getTotalUpOrder(gameId,period);
        if(null != subOrders && subOrders.size() > 0){
            Map<String,Long> valueMap = new ConcurrentSkipListMap<>();
            for (GameSubOrder subOrder : subOrders) {
                String gameRule = subOrder.getGameRule();
                String gameBetContent = subOrder.getGameBetContent();
                Long gameBetAmount = subOrder.getGameBetAmount();
                if((ObjectUtil.isInteger(gameBetContent) && ObjectUtil.isInteger(gameRule))
                        || Constants.GameRule.QIAN_SAN.equals(gameRule)
                        || Constants.GameRule.ZHONG_SAN.equals(gameRule)
                        || Constants.GameRule.HOU_SAN.equals(gameRule)){
                    String key =  gameRule + ":" + gameBetContent;
                    if(null != valueMap.get(key)){
                        valueMap.put(key,valueMap.get(key) + gameBetAmount);
                    }else{
                        valueMap.put(key, gameBetAmount);
                    }
                }else{

                    if(num == 10){
                        if(Constants.Rule.DA.contains(gameBetContent)){
                            countTenBig(gameRule,gameBetAmount,valueMap);

                        }else if(Constants.Rule.XIAO.contains(gameBetContent)){
                            countTenSmall(gameRule,gameBetAmount,valueMap);

                        }else if(Constants.Rule.DAN.contains(gameBetContent)){
                            countNumSingle(gameRule,gameBetAmount,valueMap);

                        }else if(Constants.Rule.SHUANG.contains(gameBetContent)){
                            countTenDouble(gameRule,gameBetAmount,valueMap);

                        }else if(Constants.Rule.LONG.contains(gameBetContent)){
                            Long divide = BigDecimal.valueOf(gameBetAmount).divide(BigDecimal.valueOf(18)).longValue();
                            if("1".equals(gameRule)){
                                countLH(valueMap, divide, 2, 11, 1);
                                countLH(valueMap, divide, 2, 11, 10);
                            }else if("2".equals(gameRule)){
                                countLH(valueMap, divide, 2, 11, 2);
                                countLH(valueMap, divide, 2, 11, 9);
                            }else if("3".equals(gameRule)){
                                countLH(valueMap, divide, 2, 11, 3);
                                countLH(valueMap, divide, 2, 11, 8);
                            }else if("4".equals(gameRule)){
                                countLH(valueMap, divide, 2, 11, 4);
                                countLH(valueMap, divide, 2, 11, 7);
                            }else if("5".equals(gameRule)){
                                countLH(valueMap, divide, 2, 11, 5);
                                countLH(valueMap, divide, 2, 11, 6);
                            }

                        }else if(Constants.Rule.HU.contains(gameBetContent)){
                            Long divide = BigDecimal.valueOf(gameBetAmount).divide(BigDecimal.valueOf(18)).longValue();
                            if("1".equals(gameRule)){
                                countLH(valueMap, divide, 1, 9, 1);
                                countLH(valueMap, divide, 1, 9, 10);
                            }else if("2".equals(gameRule)){
                                countLH(valueMap, divide, 1, 9, 2);
                                countLH(valueMap, divide, 1, 9, 9);
                            }else if("3".equals(gameRule)){
                                countLH(valueMap, divide, 1, 9, 3);
                                countLH(valueMap, divide, 1, 9, 8);
                            }else if("4".equals(gameRule)){
                                countLH(valueMap, divide, 1, 9, 4);
                                countLH(valueMap, divide, 1, 9, 7);
                            }else if("5".equals(gameRule)){
                                countLH(valueMap, divide, 1, 9, 5);
                                countLH(valueMap, divide, 1, 9, 6);
                            }
                        }else if(Constants.GameRule.GUANG_YA_HE.contains(gameRule)){
                            if(ObjectUtil.isInteger(gameBetContent)){
                                countGYH(valueMap, gameBetAmount, Integer.valueOf(gameBetContent));
                            }else if(Constants.Rule.DA.contains(gameBetContent)){
                                //冠亚和大 12至20
                                long divide = getDivide(gameBetAmount, 8);
                                for (int i = 12; i < 20; i++) {
                                    countGYH(valueMap, divide, i);
                                }
                            }else if(Constants.Rule.XIAO.contains(gameBetContent)){
                                //冠亚和小 3至11
                                long divide = getDivide(gameBetAmount, 9);
                                for (int i = 3; i < 12; i++) {
                                    countGYH(valueMap, divide, i);
                                }
                            }else if(Constants.Rule.DAN.contains(gameBetContent)){
                                //冠亚和单
                                long divide = getDivide(gameBetAmount, gySingle.length);
                                for (Integer s : gySingle) {
                                    countGYH(valueMap, divide, s);
                                }
                            }else if(Constants.Rule.SHUANG.contains(gameBetContent)){
                                //冠亚和双
                                long divide = getDivide(gameBetAmount, gyDouble.length);
                                for (Integer s : gyDouble) {
                                    countGYH(valueMap, divide, s);
                                }
                            }
                        }
                    }

                    else if(num == 5){

                        if(Constants.Rule.DA.contains(gameBetContent)){
                            if(ObjectUtil.isInteger(gameRule)){
                                countFiveBig(gameRule,gameBetAmount,valueMap);

                            }else{
                                //总和大
                                long divide = getDivide(gameBetAmount, 23);
                                for (int i = 23; i < 46; i++) {
                                    countFiveNumber(valueMap, divide, i);
                                }
                            }

                        }else if(Constants.Rule.XIAO.contains(gameBetContent)){
                            if(ObjectUtil.isInteger(gameRule)){
                                countFiveSmall(gameRule,gameBetAmount,valueMap);
                            }else{
                                //总和小
                                long divide = getDivide(gameBetAmount, 23);
                                for (int i = 0; i < 23; i++) {
                                    countFiveNumber(valueMap, divide, i);
                                }
                            }

                        }else if(Constants.Rule.DAN.contains(gameBetContent)){
                            if(ObjectUtil.isInteger(gameRule)){
                                countNumSingle(gameRule,gameBetAmount,valueMap);

                            }else{
                                //总和单
                                long divide = getDivide(gameBetAmount, fiveHeSingle.length);
                                for (Integer s : fiveHeSingle) {
                                    countFiveNumber(valueMap, divide, s);
                                }
                            }

                        }else if(Constants.Rule.SHUANG.contains(gameBetContent)){
                            if(ObjectUtil.isInteger(gameRule)){
                                countFiveDouble(gameRule,gameBetAmount,valueMap);
                            }else{
                                //总和双
                                long divide = getDivide(gameBetAmount, fiveHeDouble.length);
                                for (Integer s : fiveHeDouble) {
                                    countFiveNumber(valueMap, divide, s);
                                }
                            }

                        }else if(Constants.Rule.LONG.contains(gameBetContent)){
                            Long divide = getDivide(gameBetAmount,18);
                            countLH(valueMap, divide, 1, 10, 1);
                            countLH(valueMap, divide, 1, 10, 5);

                        }else if(Constants.Rule.HU.contains(gameBetContent)){
                            Long divide = getDivide(gameBetAmount,18);
                            countLH(valueMap, divide, 0, 9, 1);
                            countLH(valueMap, divide, 0, 9, 5);

                        }else if(Constants.Rule.HE.contains(gameBetContent)){
                            Long divide = getDivide(gameBetAmount,20);
                            countLH(valueMap, divide, 0, 10, 1);
                            countLH(valueMap, divide, 0, 10, 5);
                        }
                    }

                    else if(num == 3){
                        if(Constants.GameRule.ZONG_HE.contains(gameRule)){
                            if(ObjectUtil.isInteger(gameBetContent)){
                                countThreeNumber(valueMap, gameBetAmount, Integer.valueOf(gameBetContent).intValue());

                            }else if(Constants.Rule.DA.contains(gameBetContent)){
                                //总和大
                                long divide = getDivide(gameBetAmount, 14);
                                for (int i = 14; i < 28; i++) {
                                    countThreeNumber(valueMap, divide, i);
                                }

                            }else if(Constants.Rule.XIAO.contains(gameBetContent)){
                                //总和小
                                long divide = getDivide(gameBetAmount, 14);
                                for (int i = 0; i < 14; i++) {
                                    countThreeNumber(valueMap, divide, i);
                                }

                            }else if(Constants.Rule.DAN.contains(gameBetContent)){
                                //总和单
                                long divide = getDivide(gameBetAmount, threeSingle.length);
                                for (Integer s : threeSingle) {
                                    countThreeNumber(valueMap, divide, s);
                                }

                            }else if(Constants.Rule.SHUANG.contains(gameBetContent)){
                                //总和双
                                long divide = getDivide(gameBetAmount, threeDouble.length);
                                for (Integer s : threeDouble) {
                                    countThreeNumber(valueMap, divide, s);
                                }

                            }else if(Constants.Rule.DA_DAN.contains(gameBetContent)){
                                long divide = getDivide(gameBetAmount, threeDaDan.length);
                                for (Integer s : threeDaDan) {
                                    countThreeNumber(valueMap, divide, s);
                                }

                            }else if(Constants.Rule.XIAO_DAN.contains(gameBetContent)){
                                long divide = getDivide(gameBetAmount, threeXiaoDan.length);
                                for (Integer s : threeXiaoDan) {
                                    countThreeNumber(valueMap, divide, s);
                                }

                            }else if(Constants.Rule.DA_SHUANG.contains(gameBetContent)){
                                long divide = getDivide(gameBetAmount, threeDaShuang.length);
                                for (Integer s : threeDaShuang) {
                                    countThreeNumber(valueMap, divide, s);
                                }

                            }else if(Constants.Rule.XIAO_SHUANG.contains(gameBetContent)){
                                long divide = getDivide(gameBetAmount, threeXiaoShuang.length);
                                for (Integer s : threeXiaoShuang) {
                                    countThreeNumber(valueMap, divide, s);
                                }

                            }

                        }else if(Constants.GameRule.BO_DUAN.contains(gameRule)){
                            long divide = getDivide(gameBetAmount, 8);

                            if(Constants.Rule.HONG_BO.equals(gameBetContent)){
                                for (Integer s : hongBo) {
                                    countThreeNumber(valueMap, divide, s);
                                }

                            }else if(Constants.Rule.LV_BO.equals(gameBetContent)){
                                for (Integer s : lvBo) {
                                    countThreeNumber(valueMap, divide, s);
                                }

                            }else if(Constants.Rule.LAN_BO.equals(gameBetContent)){
                                for (Integer s : lanBo) {
                                    countThreeNumber(valueMap, divide, s);
                                }
                            }
                        }else if(Constants.GameRule.JI_ZHI.contains(gameRule)){
                            long divide = getDivide(gameBetAmount, 6);

                            if(Constants.Rule.JI_DA.equals(gameBetContent)){
                                for (int i = 0; i < 6; i++) {
                                    countThreeNumber(valueMap, divide, i);
                                }

                            }else if(Constants.Rule.JI_XIAO.equals(gameBetContent)){
                                for (int i = 22; i < 28; i++) {
                                    countThreeNumber(valueMap, divide, i);
                                }

                            }
                        }
                    }
                }
            }

            // 升序比较器
            Comparator<Map.Entry<String, Long>> valueComparator = new Comparator<Map.Entry<String,Long>>() {
                @Override
                public int compare(Map.Entry<String, Long> o1,
                                   Map.Entry<String, Long> o2) {

                    return o1.getValue().compareTo(o2.getValue());
                }
            };

            // map转换成list进行排序
            List<Map.Entry<String, Long>> list = new ArrayList<>(valueMap.entrySet());

            // 排序
            Collections.sort(list,valueComparator);

            Map<String,Long> result = new LinkedHashMap<>();
            for (Map.Entry<String, Long> entry : list) {
                result.put(entry.getKey(),entry.getValue());
            }
            valueMap.clear();
            list.clear();
            return result;
        }
        return new HashMap<>();
    }

    private void countThreeNumber(Map<String, Long> valueMap, Long gameBetAmount, int i) {
        if (null != valueMap.get(i + "")) {
            valueMap.put(i + "", valueMap.get(i + "") + gameBetAmount);
        } else {
            valueMap.put(i + "", gameBetAmount);
        }
    }

    private void countFiveNumber(Map<String, Long> valueMap, Long gameBetAmount, int i) {
        Set<Integer> nums = getNums(fiveZH, i);
        long divide = getDivide(gameBetAmount, nums.size() * 5);
        for (Integer is : nums) {
            for (int j = 1; j < 6; j++) {
                String key = j + ":" + is;
                if (null != valueMap.get(key)) {
                    valueMap.put(key, valueMap.get(key) + divide);
                } else {
                    valueMap.put(key, divide);
                }
            }
        }
    }

    private void countLH(Map<String, Long> valueMap, Long divide, int i2, int i3, int i4) {
        for (int i = i2; i < i3; i++) {
            String key = i4 + ":" + i;
            if (null != valueMap.get(key)) {
                valueMap.put(key, valueMap.get(key) + divide);
            } else {
                valueMap.put(key, divide);
            }
        }
    }

    private void countGYH(Map<String, Long> valueMap,  Long gameBetAmount, int i) {
        List<String> nums = getNums(tenGYH, i, tenString, tenString);
        Long divide = getDivide(gameBetAmount, nums.size() * 4);
        int flag = i/2;
        //10:2  第一名和第二名可以是10 2
        for (String str : nums) {
            String[] split = str.split(":");
            for (String val : split) {
                if(Integer.valueOf(val).intValue() >= flag){
                    if("10".equals(val)) {
                        val = "0";
                    }
                    for (int j = 1; j < 3; j++) {
                        String key = j + ":" + val;
                        if (null != valueMap.get(key)) {
                            valueMap.put(key, valueMap.get(key) + divide);
                        } else {
                            valueMap.put(key, divide);
                        }
                    }
                }
            }
        }
    }

    private void countFiveDouble(String gameRule, Long gameBetAmount, Map<String, Long> valueMap) {
        Long divide = getDivide(gameBetAmount, 5);
        for (Integer val : fiveDouble) {
            String key =  gameRule + ":" + val;
            if(null != valueMap.get(key)){
                valueMap.put(key,valueMap.get(key) + divide);
            }else{
                valueMap.put(key,divide);
            }
        }
    }

    private void countFiveSmall(String gameRule, Long gameBetAmount, Map<String, Long> valueMap) {
        //0-4 小
        Long divide = getDivide(gameBetAmount, 5);
        for (int i = 0; i < 5; i++) {
            String key =  gameRule + ":" + i;
            if(null != valueMap.get(key)){
                valueMap.put(key,valueMap.get(key) + divide);
            }else{
                valueMap.put(key,divide);
            }
        }
    }

    private void countFiveBig(String gameRule, Long gameBetAmount, Map<String, Long> valueMap) {
        //5-9 大
        Long divide = getDivide(gameBetAmount, 5);
        for (int i = 5; i < 10; i++) {
            String key =  gameRule + ":" + i;
            if(null != valueMap.get(key)){
                valueMap.put(key,valueMap.get(key) + divide);
            }else{
                valueMap.put(key,divide);
            }
        }
    }

    private void countTenDouble(String gameRule, Long gameBetAmount, Map<String, Long> valueMap) {
        Long divide = getDivide(gameBetAmount, 5);
        for (Integer val : tenDouble) {
            if(val == 10) {
                val = 0;
            }
            String key =  gameRule + ":" + val;
            if(null != valueMap.get(key)){
                valueMap.put(key,valueMap.get(key) + divide);
            }else{
                valueMap.put(key,divide);
            }
        }
    }

    private void countNumSingle(String gameRule, Long gameBetAmount, Map<String, Long> valueMap) {
        Long divide = getDivide(gameBetAmount, 5);
        for (Integer val : numSingle) {
            String key =  gameRule + ":" + val;
            if(null != valueMap.get(key)){
                valueMap.put(key,valueMap.get(key) + divide);
            }else{
                valueMap.put(key,divide);
            }
        }
    }

    private void countTenSmall(String gameRule, Long gameBetAmount, Map<String, Long> valueMap) {
        //1-5 小
        Long divide = getDivide(gameBetAmount, 5);
        for (int i = 1; i < 6; i++) {
            String key =  gameRule + ":" + i;
            if(null != valueMap.get(key)){
                valueMap.put(key,valueMap.get(key) + divide);
            }else{
                valueMap.put(key,divide);
            }
        }
    }

    private void countTenBig(String gameRule, Long gameBetAmount, Map<String, Long> valueMap) {
        //6-10 大
        Long divide = getDivide(gameBetAmount, 5);
        for (int i = 6; i < 11; i++) {
            if(i == 10) {
                i = 0;
            }
            String key =  gameRule + ":" + i;
            Long keyV = valueMap.get(key);
            if(null != keyV){
                valueMap.put(key, keyV + divide);
            }else{
                valueMap.put(key,divide);
            }
        }
    }

    public static void main(String[] args) {
        Map<String,Long> valueMap = new ConcurrentSkipListMap<>();
        for (int j = 0; j < 100000; j++) {
            Long divide = BigDecimal.valueOf(10000).divide(BigDecimal.valueOf(5), 0, BigDecimal.ROUND_UP).longValue();
            for (int i = 6; i < 11; i++) {
                if(i == 10) {
                    i = 0;
                }
                String key =  "5" + ":" + i;
                Long keyV = valueMap.get(key);
                if(null != keyV){
                    valueMap.put(key, keyV + divide);
                }else{
                    valueMap.put(key,divide);
                }
            }
        }
        System.out.println("结束");
    }

    private long getDivide(Long gameBetAmount, int i) {
        return BigDecimal.valueOf(gameBetAmount).divide(BigDecimal.valueOf(i), 0, BigDecimal.ROUND_UP).longValue();
    }

    private void openLogSet(int openNum,String openPeriod,OpenLog log) {

        log.setOpenNumber(openPeriod);

        String[] split = openPeriod.split(",");

        setFirstFifthNum(split,log);

        int[] array = Arrays.asList(split).stream().mapToInt(Integer::parseInt).toArray();
        if(openNum == 5){
            //5球
            log.setGuang(array[0] + array[1] + array[2] + array[3] + array[4]);

            if(log.getGuang() > 22){
                log.setYa(Constants.Rule.DA);
            }else{
                log.setYa(Constants.Rule.XIAO);
            }

            log.setFirstDt(array[0] > array[4] ? Constants.Rule.LONG : array[0] == array[4] ? Constants.Rule.HE : Constants.Rule.HU);
            setHe(log);
            OpenUtil.setThree(log);

        }else if(openNum == 10){

            setSixTenNum(split,log);
            setTenGuangYaHe(log);
            OpenUtil.setDt(split,log);

        }else if(openNum == 3){

            log.setGuang(array[0] + array[1] + array[2]);
            if(log.getGuang() > 13){
                log.setYa(Constants.Rule.DA);
            }else{
                log.setYa(Constants.Rule.XIAO);
            }
            setHe(log);
        }
    }

    private void setTenGuangYaHe(OpenLog log) {

        log.setGuang(log.getFirstNum() + log.getSecondNum());
        if(log.getGuang() >= 12){
            log.setYa(Constants.Rule.DA);
        }else{
            log.setYa(Constants.Rule.XIAO);
        }
        setHe(log);
    }

    private void setHe(OpenLog log) {
        if (log.getGuang() % 2 == 0) {
            log.setHe(Constants.Rule.SHUANG);
        } else {
            log.setHe(Constants.Rule.DAN);
        }
    }

    private void setFirstFifthNum(String[] split, OpenLog log) {
        log.setFirstNum(Integer.valueOf(split[0]));
        log.setSecondNum(Integer.valueOf(split[1]));
        log.setThirdNum(Integer.valueOf(split[2]));
        if(log.getGameId() != 9){
            log.setFourthNum(Integer.valueOf(split[3]));
            log.setFifthNum(Integer.valueOf(split[4]));
        }
    }

    private void setSixTenNum(String[] split, OpenLog log) {
        log.setSixthNum(Integer.valueOf(split[5]));
        log.setSeventhNum(Integer.valueOf(split[6]));
        log.setEighthNum(Integer.valueOf(split[7]));
        log.setNinthNum(Integer.valueOf(split[8]));
        log.setTenthNum(Integer.valueOf(split[9]));
    }

    public static String openFiveNum(){
        String str = "";
        String entity = "01234567890123456789012345678901234567890123456789";
        List<String> stringList = Arrays.asList(entity.split(""));
        shuffle(stringList);
        for(String listItem : stringList){
            str = str + listItem;
        }
        Random random = new Random();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 5; i++) {
            int number = random.nextInt(str.length());
            char charAt = str.charAt(number);
            String str1 = str.substring(0, number);
            String str2 = str.substring(number + 1);
            str = str1 + str2;
            sb.append(charAt);
            sb.append(",");
        }
        String result = sb.toString();
        return result.substring(0, result.length() -1);
    }

    public static String openRandomNum(int count){
        if(count == 5){
            return openFiveNum();
        }
        Random ran = new Random();
        int[] tmp = new int[10];
        for(int i = 0; i < tmp.length; i++){
            tmp[i] = i + 1;
        }

        int[] arr = new int[count];

        for(int i = 0; i < arr.length; i++){
            int index = ran.nextInt(tmp.length-i);
            arr[i] = tmp[index]; //随机下标
            if(count == 10) {
                tmp[index] = tmp[tmp.length -1 -i];
            }
        }

        StringBuilder builder = new StringBuilder();
        for(int i=0; i< arr.length; i++){
            builder.append(arr[i] + ",");
        }

        String openNum = builder.toString().substring(0, builder.length() - 1);
        if(count == 5 || count == 3){
            openNum = openNum.replaceAll("10","0");
        }
        return openNum;
    }

    @Override
    public Response periodFix(Integer gameId, String selectTime) throws JsonProcessingException, InterruptedException {
        String today = DateUtil.today();
        List<Long> periods;
        if(null == selectTime || selectTime.equals(today)){
            periods =  openLogMapper.getOpenPeriod(gameId, today + " 00:00:00",DateUtil.now());
        }else{
            periods =  openLogMapper.getOpenPeriod(gameId, selectTime + " 00:00:00",selectTime + " 23:59:59");
        }
        if(gameId == 1 || gameId == 4 || gameId == 6 || gameId == 9 || gameId == 10 || gameId == 11){
            //自己开
            List<Long> originalPeriods;
            if(null == selectTime || selectTime.equals(today)){
                originalPeriods = taskGameOpenTimeMapper.getOpenPeriod(gameId,DateUtil.today() + " 00:00:00",DateUtil.offsetMinute(new Date(), -4));
            }else{
                originalPeriods = taskGameOpenTimeMapper.getOpenPeriod(gameId,selectTime+ " 00:00:00",DateUtil.parseDateTime(selectTime + " 23:59:59"));
            }
            for (Long per : originalPeriods) {
                if(!periods.contains(per)){
                    this.openDraw(gameId,per);
                }
            }
        }else{
            //dsn
            JSONArray data = JSONObject.parseObject(HttpUtil.get(dsnHost + DsnFixUrlCode.getValueByCode(gameId))).getJSONArray("data");
            if(null != data){
                for (int i = 0; i < data.size(); i++) {
                    JSONObject json = data.getJSONObject(i);
                    if(!periods.contains(json.getLong("drawNumber"))){
                        switch (gameId){
                            case 2 :
                                dsnService.xyftDraw(json);
                                break;
                            case 3 :
                                dsnService.azxy10Draw(json);
                                break;
                            case 5 :
                                dsnService.jsscDraw(json);
                                break;
                            case 7 :
                                dsnService.cqsscDraw(json);
                                break;
                            case 8 :
                                dsnService.azxy5Draw(json);
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
        }
        return Response.success();
    }

    @Override
    public String getOpen(OpenLog log,Integer num) {
        return fixedAmountOpen(10000000L, num, log);
    }

    @Override
    public String getOpenMixed(OpenLog log, Integer num) {
        return mixedModelOpen(num,log);
    }

    @Override
    public void openLiuDraw(int gameId) {
        GameOpenTime time = taskGameOpenTimeMapper.getLhcBeforePeriod(gameId);
        if(null == time) {
            return;
        }

        Long period = time.getPeriod();
        OpenLog log = openLogMapper.getOpenLogByPeriod(gameId, period);
        if(null != log) {
            return;
        }

        OpenLog openLog = new OpenLog();
        openLog.setPeriod(time.getPeriod());
        openLog.setOpenTime(time.getEndTime());
        openLog.setGameId(gameId);
        openLog.setCreateTime(new Date());

        String result = HttpUtil.get("http://172.21.140.84/util/util/sendGetUrl?url=" + DsnFixUrlCode.getValueByCode(gameId));
        String res = result.split(";")[0];
        Long findP = Long.parseLong(res.substring(0,7));
        if(findP.equals(period)){
            if(gameId == 12){
                openLog.setGameName("GA6");
            }else{
                openLog.setGameName("AM6");
            }
            openLog.setOpenNumber(res.substring(8));
            String[] sp = res.substring(8).split(",");
            setFirstFifthNum(sp,openLog);
            openLog.setSixthNum(Integer.valueOf(sp[5]));
            openLog.setSeventhNum(Integer.valueOf(sp[6]));
        }

        if(null != openLog.getOpenNumber()){
            openLog.setGuang(openLog.getFirstNum()  + openLog.getSecondNum() + openLog.getThirdNum()
                    + openLog.getFourthNum() + openLog.getFifthNum()  + openLog.getSixthNum() + openLog.getSeventhNum());
            openLog.setHe((openLog.getGuang() % 2 == 0) ? Constants.Rule.SHUANG : Constants.Rule.DAN);
            //大 大于或等于175
            openLog.setYa((openLog.getGuang() > 174) ? Constants.Rule.DA : Constants.Rule.XIAO);
            List<LhcZodiac> lhcZodiacs = lhcZodiacService.getZodiacByType(0);
            openLog.setZodiac(getLhcZodiac(openLog.getOpenNumber(), lhcZodiacs));
            openLog.setColors(getLhcColors(openLog.getOpenNumber()));
            openLogMapper.insertSelective(openLog);
        }
    }

    private String getLhcZodiac(String numbers, List<LhcZodiac> list){
        String[] number = numbers.split(",");
        String result = "";
        for (String s : number) {
            int num = Integer.parseInt(s);
            for (LhcZodiac lhcZodiac : list) {
                String zNumbers = lhcZodiac.getNumbers();
                String[] zNumber = zNumbers.split(",");
                for (String value : zNumber) {
                    int zNum = Integer.parseInt(value);
                    if (zNum == num) {
                        result = result + lhcZodiac.getZodiac() + ",";
                        break;
                    }
                }
            }
        }
        result = result.substring(0, result.length() -1);
        return result;
    }

    private String getLhcColors(String numbers){
        String[] number = numbers.split(",");
        String result = "";
        for (String str : number) {
            int num = Integer.parseInt(str);
            if(num == 1 || num == 2 || num == 7 || num == 8 || num == 12 || num == 13 || num == 18 || num == 19 || num == 23 || num == 24 || num == 29 || num == 30 || num == 34 || num == 35 || num == 40 || num == 45 || num == 46){
                result = result + "红,";
            }else if(num == 3 || num == 4 || num == 9 || num == 10 || num == 14 || num == 15 || num == 20 || num == 25 || num == 26 || num == 31 || num == 36 || num == 37 || num == 41 || num == 42 || num == 47 || num == 48){
                result = result + "蓝,";
            }else{
                result = result + "绿,";
            }
        }
        result = result.substring(0, result.length() -1);
        return result;
    }
}

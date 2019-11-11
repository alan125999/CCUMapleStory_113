﻿
/* Author: Xterminator
	NPC Name: 		Tian
	Map(s): 		Ludibrium: Station<Orbis> (220000110)
	Description: 		Ludibrium Ticketing Usher
*/
var status = 0;

function start() {
    status = -1;
    train = cm.getEventManager("Trains");
    action(1, 0, 0);
}

function action(mode, type, selection) {
    status++;
    if (mode == 0) {
        cm.sendNext("等你考慮好再來找我。");
        cm.dispose();
        return;
    }
    if (status == 0) {
        if (train == null) {
            cm.sendNext("找不到腳本請聯繫GM！");
            cm.dispose();
        } else if (train.getProperty("entry").equals("true")) {
            cm.sendYesNo("你想要搭船？？");
        } else if (train.getProperty("entry").equals("false") && train.getProperty("docked").equals("true")) {
            cm.sendNext("很抱歉本班船準備開走,乘坐時間表可以通過售票展台查看.");
            cm.dispose();
        } else {
            cm.sendNext("很抱歉本班船已經走了,乘坐時間表可以通過售票展台查看.");
            cm.dispose();
        }
    } else if (status == 1) {
        if (!cm.haveItem(4031045)) {
            cm.sendNext("不! 你沒有#b#t4031045##k 所以我不能放你走!");
        } else {
            cm.gainItem(4031045, -1);
            cm.warp(220000111, 0);
        }
        cm.dispose();
    }
}
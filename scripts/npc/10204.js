/*
	NPC Name: 		Kyrin
	Map(s): 		Maple Road : Spilt road of choice
	Description: 		Job tutorial, movie clip
*/

var status = -1;

function start() {
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == 1) {
        status++;
    } else {
        if (status == 1) {
            cm.sendOk("滾開.");
            cm.dispose();
            return;
        }
        status--;
    }
    if (status == 0) {
        cm.sendNext("想成為海盜嗎?");
    } else if (status == 1) {
        cm.sendYesNo("要我短暫幫你實現夢想嗎?");
    } else if (status == 2) {
        cm.MovieClipIntroUI(true);
        cm.warp(1020500, 0); // Effect/Direction3.img/magician/Scene00
        cm.dispose();
    }
}
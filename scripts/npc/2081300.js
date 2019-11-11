/*  NPC : ��N��
 �}�b�� 4�� ���ȸ}��
 �a�ϥN�X (240010501)
 */

var status = -1;
var pass = false;

function start() {
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == 0 && status == 0) {
        cm.dispose();
        return;
    }
    if (mode == 1)
        status++;
    else
        status--;

    if (status == 0) {
        if (!(cm.getJob() == 311 || cm.getJob() == 321)) {
            cm.sendOk("������A�n����??�٦��A�Q�n�ݧ����󤰻�Ʊ�??");
            cm.dispose();
            return;
        } else if (cm.getPlayer().getLevel() < 120) {
            cm.sendOk("�A���ũ|����F120��.");
            cm.dispose();
            return;
        } else {
            if (cm.getQuestStatus(6924) == 2) {
                pass = true;
            }
            if (cm.getJob() == 311) {
                cm.sendSimple("���ߧA�����4��. \r\n�аݧA�Q4���??\r\n#b#L0#�ڷQ�����b��.#l\r\n#b#L1#���ڷQ�@�U...#l");
            } else if (cm.getJob() == 321) {
                cm.sendSimple("���ߧA�����4��. \r\n�аݧA�Q4���??\r\n#b#L0#�ڷQ�������g��.#l\r\n#b#L1#���ڷQ�@�U...#l");
            } else {
                cm.sendOk("�n�a���p�A�Q�n4��·ЦA�ӧ��");
                cm.dispose();
                return;
            }
        }
    } else if (status == 1) {
        if (selection == 1) {
            cm.sendOk("�n�a���p�A�Q�n4��·ЦA�ӧ��");
            cm.dispose();
            return;
        }
        if (cm.getPlayerStat("RSP") > (cm.getPlayerStat("LVL") - 120) * 3) {
            cm.sendOk("�A���ޯ��I���٨S�I��..");
            cm.dispose();
            return;
        }
        if (pass) {
            cm.sendNext("�Y�N�|��C");
        } else {
            if (!cm.haveItem(4031860) || !cm.haveItem(4031861)) {
                cm.sendOk("�ڻݭn#t4031860# x1 #t4031861# x1�C");
                cm.dispose();
                return;
            } else {
                cm.sendNext("�Y�N�|��C");
            }
        }
    } else if (status == 2) {
        if (cm.canHold(2280003)) {
            cm.gainItem(2280003, 1);
            if (cm.getJob() == 311) {
                cm.changeJob(312);
                cm.teachSkill(3120005, 0, 10);
                cm.teachSkill(3121007, 0, 10);
                cm.teachSkill(3121002, 0, 10);
                cm.gainItem(4031860, -1);
                cm.gainItem(4031861, -1);
                cm.sendNext("���ߧA��¾�� #b�b��#k.�ڰe�A�@�ǯ����p§��^^");
            } else {
                cm.changeJob(322);
                cm.teachSkill(3221006, 0, 10);
                cm.teachSkill(3220004, 0, 10);
                cm.teachSkill(3221002, 0, 10);
                cm.gainItem(4031860, -1);
                cm.gainItem(4031861, -1);
                cm.sendNext("���ߧA��¾�� #b���g��#k.�ڰe�A�@�ǯ����p§��^^");
            }
        } else {
            cm.sendOk("�A�S���h�����вM�ŦA�ӹ��դ@��!");
            cm.dispose();
            return;
        }

    } else if (status == 3) {
        cm.sendNext("���n�ѰO�F�o�@�������M��A�m�F�h��.");
    } else if (status == 4) {
        cm.sendNextPrev("�ڤw�A���a.");
        cm.dispose();
    }
}
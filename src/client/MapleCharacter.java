/*
 This file is part of the OdinMS Maple Story Server
 Copyright (C) 2008 ~ 2010 Patrick Huy <patrick.huy@frz.cc> 
 Matthias Butz <matze@odinms.de>
 Jan Christian Meyer <vimes@odinms.de>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License version 3
 as published by the Free Software Foundation. You may not use, modify
 or distribute this program under any other version of the
 GNU Affero General Public License.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package client;

import constants.GameConstants;
import client.inventory.MapleInventoryType;
import client.inventory.MapleInventory;
import client.inventory.Item;
import client.inventory.ItemLoader;
import client.inventory.MapleInventoryIdentifier;
import client.inventory.IItem;
import client.inventory.MapleMount;
import client.inventory.MaplePet;
import client.inventory.ItemFlag;
import client.inventory.MapleRing;
import java.awt.Point;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Deque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.Serializable;

import client.anticheat.CheatTracker;
import client.inventory.Equip;
import client.inventory.ModifyInventory;
import client.status.MonsterStatus;
import client.status.MonsterStatusEffect;
import constants.ServerConstants;
import database.DatabaseConnection;
import database.DatabaseException;
import handling.MaplePacket;
import handling.channel.ChannelServer;
import handling.world.CharacterTransfer;
import handling.world.MapleAntiMacro;
import handling.world.MapleMessenger;
import handling.world.MapleMessengerCharacter;
import handling.world.MapleParty;
import handling.world.MaplePartyCharacter;
import handling.world.PartyOperation;
import handling.world.PlayerBuffStorage;
import handling.world.PlayerBuffValueHolder;
import handling.world.World;
import handling.world.family.MapleFamily;
import handling.world.family.MapleFamilyBuff;
import handling.world.family.MapleFamilyBuff.MapleFamilyBuffEntry;
import handling.world.family.MapleFamilyCharacter;
import handling.world.guild.MapleGuild;
import handling.world.guild.MapleGuildCharacter;
import java.lang.ref.WeakReference;
import java.sql.Statement;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import tools.MockIOSession;
import scripting.EventInstanceManager;
import scripting.NPCScriptManager;
import server.MaplePortal;
import server.MapleShop;
import server.MapleStatEffect;
import server.MapleStorage;
import server.MapleTrade;
import server.Randomizer;
import server.MapleCarnivalParty;
import server.MapleItemInformationProvider;
import server.life.MapleMonster;
import server.maps.AbstractAnimatedMapleMapObject;
import server.maps.MapleDoor;
import server.maps.MapleMap;
import server.maps.MapleMapFactory;
import server.maps.MapleMapObject;
import server.maps.MapleMapObjectType;
import server.maps.MapleSummon;
import server.maps.FieldLimitType;
import server.maps.SavedLocationType;
import server.quest.MapleQuest;
import server.shops.IMaplePlayerShop;
import server.CashShop;
import server.FishingRewardFactory;
import server.FishingRewardFactory.FishingReward;
import tools.MaplePacketCreator;
import tools.Pair;
import tools.packet.*;
import server.MapleCarnivalChallenge;
import server.MapleInventoryManipulator;
import server.Timer.BuffTimer;
import server.Timer.EtcTimer;
import server.Timer.MapTimer;
import server.life.MapleLifeFactory;
import server.life.MobSkill;
import server.life.PlayerNPC;
import server.maps.Event_PyramidSubway;
import server.maps.MapleFoothold;
import server.movement.LifeMovementFragment;
import tools.ConcurrentEnumMap;
import tools.FilePrinter;
import tools.HexTool;

public class MapleCharacter extends AbstractAnimatedMapleMapObject implements Serializable {

    public int pachinkoLight = 0, pachinkoOpenStage = 0, pachinkoOpenTime;
    private static final long serialVersionUID = 845748950829L;
    private String name, chalkBoardText, BlessOfFairy_Origin, charmessage, prefix, chattitle;
    private long lastCombo, lastfametime, keydownSkillTime, lastRecoveryTime, lastSummonTime;
    private byte dojoRecord, gmLevel, gender, initialSpawnPoint, skinColor, guildrank = 5, allianceRank = 5, world, fairyExp = 10, numClones, subcategory, fairyHour = 1; // Make this a quest record, TODO : Transfer it somehow with the current data
    private short level, mulung_energy, combo, availableCP, totalCP, fame, hpmpApUsed, job, remainingAp;
    private int accountid, id, meso, exp, hair, face, mapid, bookCover, dojo,
            guildid = 0, fallcounter = 0, maplepoints, acash, chair, itemEffect, points, vpoints,
            rank = 1, rankMove = 0, jobRank = 1, jobRankMove = 0, marriageId, marriageItemId = 0,
            currentrep, totalrep, linkMid = 0, coconutteam = 0, followid = 0, battleshipHP = 0,
            expression, constellation, blood, month, day, pachinkoBalls, beansNum, beansRange,
            gachexp;
    private boolean canSetBeansNum;
    private Point old = new Point(0, 0);
    private boolean smega, hidden, hasSummon = false;
    private int[] wishlist, rocks, savedLocations, regrocks, remainingSp = new int[10];
    private transient AtomicInteger inst;
    private transient List<LifeMovementFragment> lastres;
    private List<Integer> lastmonthfameids;
    private List<MapleDoor> doors;
    private List<MaplePet> pets;
    private transient WeakReference<MapleCharacter>[] clones;
    private transient Set<MapleMonster> controlled;
    private transient Set<MapleMapObject> visibleMapObjects;
    private transient ReentrantReadWriteLock visibleMapObjectsLock;
    private final Map<MapleQuest, MapleQuestStatus> quests;
    private Map<Integer, String> questinfo;
    private final Map<ISkill, SkillEntry> skills = new LinkedHashMap<>();
    private final transient Map<MapleBuffStat, MapleBuffStatValueHolder> effects = new ConcurrentEnumMap<>(MapleBuffStat.class);
    private transient Map<Integer, MapleSummon> summons;
    private final transient Map<Integer, MapleCoolDownValueHolder> coolDowns = new LinkedHashMap<>();
    private final transient Map<MapleDisease, MapleDiseaseValueHolder> diseases = new ConcurrentEnumMap<>(MapleDisease.class);
    private CashShop cs;
    private transient Deque<MapleCarnivalChallenge> pendingCarnivalRequests;
    private transient MapleCarnivalParty carnivalParty;
    private BuddyList buddylist;
    private MonsterBook monsterbook;
    private transient CheatTracker anticheat;
    private MapleClient client;
    private PlayerStats stats;
    private transient PlayerRandomStream CRand;
    private transient MapleMap map;
    private transient MapleShop shop;
    private transient RockPaperScissors rps;
    private MapleStorage storage;
    private transient MapleTrade trade;
    private MapleMount mount;
    private final List<Integer> finishedAchievements = new ArrayList<>();
    private MapleMessenger messenger;
    private byte[] petStore;
    private transient IMaplePlayerShop playerShop;
    private MapleParty party;
    private boolean invincible = false, canTalk = true, clone = false, followinitiator = false, followon = false;
    private MapleGuildCharacter mgc;
    private MapleFamilyCharacter mfc;
    private transient EventInstanceManager eventInstance;
    private MapleInventory[] inventory;
    private SkillMacro[] skillMacros = new SkillMacro[5];
    private MapleKeyLayout keylayout;
    private transient ScheduledFuture<?> BerserkSchedule,
            dragonBloodSchedule, fairySchedule, mapTimeLimitTask, fishing;
    private long nextConsume = 0, pqStartTime = 0;
    private transient Event_PyramidSubway pyramidSubway = null;
    private transient List<Integer> pendingExpiration = null, pendingSkills = null;
    private final transient Map<Integer, Integer> movedMobs = new HashMap<>();
    private String teleportname = "";
    private boolean isSaveing = false;
    private static final ReentrantLock saveLock = new ReentrantLock();// 鎖對象
    private final ReentrantLock questLock = new ReentrantLock();
    private long giveCSpointsLasttime = 0;
    private static final String[] ariantroomleader = new String[3]; // 沙漠競技場PQ
    private static final int[] ariantroomslot = new int[3]; // 沙漠競技場PQ
    private long askmastertime, askdualtime;
    public int master = 0, apprentice = 0;
    private boolean testingdps = false;
    private long dps;
    private boolean switchHiredMerchant = false, 玩家私聊1 = false, 玩家私聊2 = false, 玩家私聊3 = false, GMinfo = false, 聊天稱號 = false, GM聊天 = false;
    private boolean isShowDebugInfo = false;
    private int saveToDBCount = 0;

    public MapleCharacter(final boolean isChannelServer) {
        super.setStance(0);
        super.setPosition(new Point(0, 0));

        inventory = new MapleInventory[MapleInventoryType.values().length];
        for (MapleInventoryType type : MapleInventoryType.values()) {
            inventory[type.ordinal()] = new MapleInventory(type);
        }
        quests = new LinkedHashMap<>(); // Stupid erev quest.
        stats = new PlayerStats(this);
        for (int i = 0; i < remainingSp.length; i++) {
            remainingSp[i] = 0;
        }
        if (isChannelServer) {
            lastCombo = 0;
            mulung_energy = 0;
            combo = 0;
            keydownSkillTime = 0;
            lastRecoveryTime = 0;
            smega = true;
            lastSummonTime = 0;
            petStore = new byte[3];
            for (int i = 0; i < petStore.length; i++) {
                petStore[i] = (byte) -1;
            }
            wishlist = new int[10];
            rocks = new int[10];
            regrocks = new int[5];
            clones = new WeakReference[25];
            for (int i = 0; i < clones.length; i++) {
                clones[i] = new WeakReference<>(null);
            }
            inst = new AtomicInteger();
            inst.set(0); // 1 = NPC/ Quest, 2 = Duey, 3 = Hired Merch store, 4 = Storage
            keylayout = new MapleKeyLayout();
            doors = new ArrayList<>();
            controlled = new LinkedHashSet<>();
            summons = new LinkedHashMap<>();
            visibleMapObjects = new LinkedHashSet<>();
            visibleMapObjectsLock = new ReentrantReadWriteLock();
            pendingCarnivalRequests = new LinkedList<>();

            savedLocations = new int[SavedLocationType.values().length];
            for (int i = 0; i < SavedLocationType.values().length; i++) {
                savedLocations[i] = -1;
            }
            questinfo = new LinkedHashMap<>();
            anticheat = new CheatTracker(this);
            pets = new ArrayList<>();

        }
    }

    public static MapleCharacter getDefault(final MapleClient client, final int type) {
        MapleCharacter ret = new MapleCharacter(false);
        ret.client = client;
        ret.map = null;
        ret.exp = 0;
        ret.gmLevel = 0;
        ret.job = (short) (type == 1 ? 0 : (type == 0 ? 1000 : (type == 3 ? 2001 : (type == 4 ? 3000 : 2000))));
        ret.pachinkoBalls = 0;
        ret.meso = 0;
        ret.level = 1;
        ret.remainingAp = 0;
        ret.fame = 0;
        ret.accountid = client.getAccID();
        ret.buddylist = new BuddyList((byte) 20);

        ret.stats.str = 12;
        ret.stats.dex = 5;
        ret.stats.int_ = 4;
        ret.stats.luk = 4;
        ret.stats.maxhp = 50;
        ret.stats.hp = 50;
        ret.stats.maxmp = 50;
        ret.stats.mp = 50;
        ret.prefix = "";
        ret.gachexp = 0;

        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps;
            ps = con.prepareStatement("SELECT * FROM accounts WHERE id = ?");
            ps.setInt(1, ret.accountid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ret.client.setAccountName(rs.getString("name"));
                    ret.acash = rs.getInt("ACash");
                    ret.maplepoints = rs.getInt("mPoints");
                    ret.points = rs.getInt("points");
                    ret.vpoints = rs.getInt("vpoints");
                }
            }
            ps.close();
        } catch (SQLException e) {
            System.err.println("Error getting character default" + e);
        }
        return ret;
    }

    public final static MapleCharacter ReconstructChr(final CharacterTransfer ct, final MapleClient client, final boolean isChannel) {
        final MapleCharacter ret = new MapleCharacter(true); // Always true, it's change channel
        ret.client = client;
        if (!isChannel) {
            ret.client.setChannel(ct.channel);
        }
        ret.getCheatTracker().lastTime = ct.lastTime;
        ret.GM聊天 = ct.GM聊天;
        ret.id = ct.characterid;
        ret.name = ct.name;
        ret.level = ct.level;
        ret.fame = ct.fame;

        ret.CRand = new PlayerRandomStream();

        ret.stats.str = ct.str;
        ret.stats.dex = ct.dex;
        ret.stats.int_ = ct.int_;
        ret.stats.luk = ct.luk;
        ret.stats.maxhp = ct.maxhp;
        ret.stats.maxmp = ct.maxmp;
        ret.stats.hp = ct.hp;
        ret.stats.mp = ct.mp;

        ret.chalkBoardText = ct.chalkboard;
        ret.exp = ct.exp;
        ret.hpmpApUsed = ct.hpApUsed;
        ret.remainingSp = ct.remainingSp;
        ret.remainingAp = ct.remainingAp;
        ret.pachinkoBalls = ct.beans;
        ret.meso = ct.meso;
        ret.gmLevel = ct.gmLevel;
        ret.skinColor = ct.skinColor;
        ret.gender = ct.gender;
        ret.job = ct.job;
        ret.hair = ct.hair;
        ret.face = ct.face;
        ret.accountid = ct.accountid;
        ret.mapid = ct.mapid;
        ret.initialSpawnPoint = ct.initialSpawnPoint;
        ret.world = ct.world;
        ret.bookCover = ct.mBookCover;
        ret.dojo = ct.dojo;
        ret.dojoRecord = ct.dojoRecord;
        ret.guildid = ct.guildid;
        ret.guildrank = ct.guildrank;
        ret.allianceRank = ct.alliancerank;
        ret.points = ct.points;
        ret.vpoints = ct.vpoints;
        ret.fairyExp = ct.fairyExp;
        ret.marriageId = ct.marriageId;
        ret.currentrep = ct.currentrep;
        ret.totalrep = ct.totalrep;
        ret.charmessage = ct.charmessage;
        ret.expression = ct.expression;
        ret.constellation = ct.constellation;
        ret.blood = ct.blood;
        ret.month = ct.month;
        ret.day = ct.day;
        ret.gachexp = ct.gachexp;
        ret.makeMFC(ct.familyid, ct.seniorid, ct.junior1, ct.junior2);
        if (ret.guildid > 0) {
            ret.mgc = new MapleGuildCharacter(ret);
        }
        ret.buddylist = new BuddyList(ct.buddysize);
        ret.subcategory = ct.subcategory;
        ret.prefix = ct.prefix;
        ret.dps = ct.dps;

        if (isChannel) {
            final MapleMapFactory mapFactory = ChannelServer.getInstance(client.getChannel()).getMapFactory();
            ret.map = mapFactory.getMap(ret.mapid);
            if (ret.map == null) { //char is on a map that doesn't exist warp it to henesys
                ret.map = mapFactory.getMap(100000000);
            } else if (ret.map.getForcedReturnId() != 999999999) {
                ret.map = ret.map.getForcedReturnMap();
            }
            MaplePortal portal = ret.map.getPortal(ret.initialSpawnPoint);
            if (portal == null) {
                portal = ret.map.getPortal(0); // char is on a spawnpoint that doesn't exist - select the first spawnpoint instead
                ret.initialSpawnPoint = 0;
            }
            ret.setPosition(portal.getPosition());

            final int messengerid = ct.messengerid;
            if (messengerid > 0) {
                ret.messenger = World.Messenger.getMessenger(messengerid);
            }
        } else {

            ret.messenger = null;
        }
        int partyid = ct.partyid;
        if (partyid >= 0) {
            MapleParty party = World.Party.getParty(partyid);
            if (party != null && party.getMemberById(ret.id) != null) {
                ret.party = party;
            }
        }

        MapleQuestStatus queststatus;
        MapleQuestStatus queststatus_from;
        MapleQuest quest;
        for (final Map.Entry<Integer, Object> qs : ct.Quest.entrySet()) {
            quest = MapleQuest.getInstance(qs.getKey());
            queststatus_from = (MapleQuestStatus) qs.getValue();

            queststatus = new MapleQuestStatus(quest, queststatus_from.getStatus());
            queststatus.setForfeited(queststatus_from.getForfeited());
            queststatus.setCustomData(queststatus_from.getCustomData());
            queststatus.setCompletionTime(queststatus_from.getCompletionTime());

            if (queststatus_from.getMobKills() != null) {
                for (final Map.Entry<Integer, Integer> mobkills : queststatus_from.getMobKills().entrySet()) {
                    queststatus.setMobKills(mobkills.getKey(), mobkills.getValue());
                }
            }
            ret.quests.put(quest, queststatus);
        }
        for (final Map.Entry<Integer, SkillEntry> qs : ct.Skills.entrySet()) {
            ret.skills.put(SkillFactory.getSkill(qs.getKey()), qs.getValue());
        }
        for (final Integer zz : ct.finishedAchievements) {
            ret.finishedAchievements.add(zz);
        }
        ret.monsterbook = new MonsterBook(ct.mbook);
        ret.inventory = (MapleInventory[]) ct.inventorys;
        ret.BlessOfFairy_Origin = ct.BlessOfFairy;
        ret.skillMacros = (SkillMacro[]) ct.skillmacro;
        ret.petStore = ct.petStore;
        ret.keylayout = new MapleKeyLayout(ct.keymap);
        ret.questinfo = ct.InfoQuest;
        ret.savedLocations = ct.savedlocation;
        ret.wishlist = ct.wishlist;
        ret.rocks = ct.rocks;
        ret.regrocks = ct.regrocks;
        ret.buddylist.loadFromTransfer(ct.buddies);
        // ret.lastfametime
        // ret.lastmonthfameids
        ret.keydownSkillTime = 0; // Keydown skill can't be brought over
        ret.lastfametime = ct.lastfametime;
        ret.lastmonthfameids = ct.famedcharacters;
        ret.storage = (MapleStorage) ct.storage;
        ret.cs = (CashShop) ct.cs;
        client.setAccountName(ct.accountname);
        ret.acash = ct.ACash;
        ret.maplepoints = ct.MaplePoints;
        ret.numClones = ct.clonez;
        ret.mount = new MapleMount(ret, ct.mount_itemid, GameConstants.isKOC(ret.job) ? 10001004 : (GameConstants.isAran(ret.job) ? 20001004 : 1004), ct.mount_Fatigue, ct.mount_level, ct.mount_exp);

        ret.stats.recalcLocalStats(true);
        ret.giveCSpointsLasttime = ct.giveCSpointsLasttime;

        return ret;
    }

    public static MapleCharacter loadCharFromDB(int charid, MapleClient client, boolean channelserver) {
        final MapleCharacter ret = new MapleCharacter(channelserver);
        ret.client = client;
        ret.id = charid;

        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = null;
        PreparedStatement pse;
        ResultSet rs = null;

        try {
            ps = con.prepareStatement("SELECT * FROM characters WHERE id = ?");
            ps.setInt(1, charid);
            rs = ps.executeQuery();
            if (!rs.next()) {
                throw new RuntimeException("Loading the Char Failed (char not found)");
            }
            ret.name = rs.getString("name");
            ret.level = rs.getShort("level");
            ret.fame = rs.getShort("fame");

            ret.stats.str = rs.getShort("str");
            ret.stats.dex = rs.getShort("dex");
            ret.stats.int_ = rs.getShort("int");
            ret.stats.luk = rs.getShort("luk");
            ret.stats.maxhp = rs.getShort("maxhp");
            ret.stats.maxmp = rs.getShort("maxmp");
            ret.stats.hp = rs.getShort("hp");
            ret.stats.mp = rs.getShort("mp");

            ret.exp = rs.getInt("exp");
            ret.hpmpApUsed = rs.getShort("hpApUsed");
            final String[] sp = rs.getString("sp").split(",");
            for (int i = 0; i < ret.remainingSp.length; i++) {
                ret.remainingSp[i] = Integer.parseInt(sp[i]);
            }
            ret.remainingAp = rs.getShort("ap");
            ret.pachinkoBalls = rs.getInt("beans");
            ret.meso = rs.getInt("meso");
            ret.gmLevel = rs.getByte("gm");
            ret.skinColor = rs.getByte("skincolor");
            ret.gender = rs.getByte("gender");
            ret.job = rs.getShort("job");
            ret.hair = rs.getInt("hair");
            ret.face = rs.getInt("face");
            ret.accountid = rs.getInt("accountid");
            ret.mapid = rs.getInt("map");
            ret.initialSpawnPoint = rs.getByte("spawnpoint");
            ret.world = rs.getByte("world");
            ret.guildid = rs.getInt("guildid");
            ret.guildrank = rs.getByte("guildrank");
            ret.allianceRank = rs.getByte("allianceRank");
            ret.currentrep = rs.getInt("currentrep");
            ret.totalrep = rs.getInt("totalrep");
            ret.makeMFC(rs.getInt("familyid"), rs.getInt("seniorid"), rs.getInt("junior1"), rs.getInt("junior2"));
            if (ret.guildid > 0) {
                ret.mgc = new MapleGuildCharacter(ret);
            }
            ret.buddylist = new BuddyList(rs.getByte("buddyCapacity"));
            ret.subcategory = rs.getByte("subcategory");
            ret.mount = new MapleMount(ret, 0, ret.job > 1000 && ret.job < 2000 ? 10001004 : (ret.job >= 2000 ? (ret.job == 2001 || (ret.job >= 2200 && ret.job <= 2218) ? 20011004 : (ret.job >= 3000 ? 30001004 : 20001004)) : 1004), (byte) 0, (byte) 1, 0);
            ret.rank = rs.getInt("rank");
            ret.rankMove = rs.getInt("rankMove");
            ret.jobRank = rs.getInt("jobRank");
            ret.jobRankMove = rs.getInt("jobRankMove");
            ret.marriageId = rs.getInt("marriageId");
            ret.charmessage = rs.getString("charmessage");
            ret.expression = rs.getInt("expression");
            ret.constellation = rs.getInt("constellation");
            ret.blood = rs.getInt("blood");
            ret.month = rs.getInt("month");
            ret.day = rs.getInt("day");
            ret.prefix = rs.getString("prefix");
            ret.gachexp = rs.getInt("gachexp");
            ret.dps = rs.getLong("dps");

            if (channelserver) {
                MapleMapFactory mapFactory = ChannelServer.getInstance(client.getChannel()).getMapFactory();
                ret.map = mapFactory.getMap(ret.mapid);
                if (ret.map == null) { //char is on a map that doesn't exist warp it to henesys
                    ret.map = mapFactory.getMap(100000000);
                }
                MaplePortal portal = ret.map.getPortal(ret.initialSpawnPoint);
                if (portal == null) {
                    portal = ret.map.getPortal(0); // char is on a spawnpoint that doesn't exist - select the first spawnpoint instead
                    ret.initialSpawnPoint = 0;
                }
                ret.setPosition(portal.getPosition());

                int partyid = rs.getInt("party");
                if (partyid >= 0) {
                    MapleParty party = World.Party.getParty(partyid);
                    if (party != null && party.getMemberById(ret.id) != null) {
                        ret.party = party;
                    }
                }
                ret.bookCover = rs.getInt("monsterbookcover");
                ret.dojo = rs.getInt("dojo_pts");
                ret.dojoRecord = rs.getByte("dojoRecord");
                final String[] pets = rs.getString("pets").split(",");
                for (int i = 0; i < ret.petStore.length; i++) {
                    ret.petStore[i] = Byte.parseByte(pets[i]);
                }
                rs.close();
                ps.close();
                ps = con.prepareStatement("SELECT * FROM achievements WHERE accountid = ?");
                ps.setInt(1, ret.accountid);
                rs = ps.executeQuery();
                while (rs.next()) {
                    ret.finishedAchievements.add(rs.getInt("achievementid"));
                }

            }
            rs.close();
            ps.close();

            boolean compensate_previousEvans = false;
            ps = con.prepareStatement("SELECT * FROM queststatus WHERE characterid = ?");
            ps.setInt(1, charid);
            rs = ps.executeQuery();
            pse = con.prepareStatement("SELECT * FROM queststatusmobs WHERE queststatusid = ?");

            while (rs.next()) {
                final int id = rs.getInt("quest");
                if (id == 170000) {
                    compensate_previousEvans = true;
                }
                final MapleQuest q = MapleQuest.getInstance(id);
                final MapleQuestStatus status = new MapleQuestStatus(q, rs.getByte("status"));
                final long cTime = rs.getLong("time");
                if (cTime > -1) {
                    status.setCompletionTime(cTime * 1000);
                }
                status.setForfeited(rs.getInt("forfeited"));
                status.setCustomData(rs.getString("customData"));
                ret.quests.put(q, status);
                pse.setInt(1, rs.getInt("queststatusid"));
                try (ResultSet rsMobs = pse.executeQuery()) {
                    while (rsMobs.next()) {
                        status.setMobKills(rsMobs.getInt("mob"), rsMobs.getInt("count"));
                    }
                }
            }
            rs.close();
            ps.close();
            pse.close();

            if (channelserver) {
                ret.CRand = new PlayerRandomStream();
                ret.monsterbook = MonsterBook.loadCards(charid);

                ps = con.prepareStatement("SELECT * FROM inventoryslot where characterid = ?");
                ps.setInt(1, charid);
                rs = ps.executeQuery();

                if (!rs.next()) {
                    throw new RuntimeException("No Inventory slot column found in SQL. [inventoryslot]");
                } else {
                    ret.getInventory(MapleInventoryType.EQUIP).setSlotLimit(rs.getByte("equip"));
                    ret.getInventory(MapleInventoryType.USE).setSlotLimit(rs.getByte("use"));
                    ret.getInventory(MapleInventoryType.SETUP).setSlotLimit(rs.getByte("setup"));
                    ret.getInventory(MapleInventoryType.ETC).setSlotLimit(rs.getByte("etc"));
                    ret.getInventory(MapleInventoryType.CASH).setSlotLimit(rs.getByte("cash"));
                }
                ps.close();
                rs.close();

                for (Pair<IItem, MapleInventoryType> mit : ItemLoader.INVENTORY.loadItems(false, charid).values()) {
                    ret.getInventory(mit.getRight()).addFromDB(mit.getLeft());
                    if (mit.getLeft().getPet() != null) {
                        ret.pets.add(mit.getLeft().getPet());
                    }
                }

                ps = con.prepareStatement("SELECT * FROM accounts WHERE id = ?");
                ps.setInt(1, ret.accountid);
                rs = ps.executeQuery();
                if (rs.next()) {
                    ret.getClient().setAccountName(rs.getString("name"));
                    ret.acash = rs.getInt("ACash");
                    ret.maplepoints = rs.getInt("mPoints");
                    ret.points = rs.getInt("points");
                    ret.vpoints = rs.getInt("vpoints");

                } else {
                    rs.close();
                }
                ps.close();

                ps = con.prepareStatement("SELECT * FROM questinfo WHERE characterid = ?");
                ps.setInt(1, charid);
                rs = ps.executeQuery();

                while (rs.next()) {
                    ret.questinfo.put(rs.getInt("quest"), rs.getString("customData"));
                }
                rs.close();
                ps.close();

                ps = con.prepareStatement("SELECT skillid, skilllevel, masterlevel, expiration FROM skills WHERE characterid = ?");
                ps.setInt(1, charid);
                rs = ps.executeQuery();
                ISkill skil;
                while (rs.next()) {
                    skil = SkillFactory.getSkill(rs.getInt("skillid"));
                    if (skil != null && GameConstants.isApplicableSkill(rs.getInt("skillid")) && rs.getByte("skilllevel") >= 0) {
                        ret.skills.put(skil, new SkillEntry(rs.getByte("skilllevel"), rs.getByte("masterlevel"), rs.getLong("expiration")));
                    } else if (skil == null) { //doesnt. exist. e.g. bb
                        ret.remainingSp[GameConstants.getSkillBookForSkill(rs.getInt("skillid"))] += rs.getByte("skilllevel");
                    }
                }
                rs.close();
                ps.close();

                ret.expirationTask(false); //do it now

                // Bless of Fairy handling
                ps = con.prepareStatement("SELECT * FROM characters WHERE accountid = ? ORDER BY level DESC");
                ps.setInt(1, ret.accountid);
                rs = ps.executeQuery();
                byte maxlevel_ = 0;
                while (rs.next()) {
                    if (rs.getInt("id") != charid) { // Not this character
                        byte maxlevel = (byte) (rs.getShort("level") / 10);

                        if (maxlevel > 20) {
                            maxlevel = 20;
                        }
                        if (maxlevel > maxlevel_) {
                            maxlevel_ = maxlevel;
                            ret.BlessOfFairy_Origin = rs.getString("name");
                        }

                    } else if (charid < 17000 && !compensate_previousEvans && ret.job >= 2200 && ret.job <= 2218) { //compensate, watch max charid
                        for (int i = 0; i <= GameConstants.getSkillBook(ret.job); i++) {
                            ret.remainingSp[i] += 2; //2 that they missed. gg
                        }
                        ret.setQuestAdd(MapleQuest.getInstance(170000), (byte) 0, null); //set it so never again
                    }
                }
                ret.skills.put(SkillFactory.getSkill(GameConstants.getBofForJob(ret.job)), new SkillEntry(maxlevel_, (byte) 0, -1));
                ps.close();
                rs.close();
                // END

                ps = con.prepareStatement("SELECT * FROM skillmacros WHERE characterid = ?");
                ps.setInt(1, charid);
                rs = ps.executeQuery();
                int position;
                while (rs.next()) {
                    position = rs.getInt("position");
                    SkillMacro macro = new SkillMacro(rs.getInt("skill1"), rs.getInt("skill2"), rs.getInt("skill3"), rs.getString("name"), rs.getInt("shout"), position);
                    ret.skillMacros[position] = macro;
                }
                rs.close();
                ps.close();

                ps = con.prepareStatement("SELECT `key`,`type`,`action` FROM keymap WHERE characterid = ?");
                ps.setInt(1, charid);
                rs = ps.executeQuery();

                final Map<Integer, Pair<Byte, Integer>> keyb = ret.keylayout.Layout();
                while (rs.next()) {
                    keyb.put(rs.getInt("key"), new Pair<>(rs.getByte("type"), rs.getInt("action")));
                }
                rs.close();
                ps.close();

                ps = con.prepareStatement("SELECT `locationtype`,`map` FROM savedlocations WHERE characterid = ?");
                ps.setInt(1, charid);
                rs = ps.executeQuery();
                while (rs.next()) {
                    ret.savedLocations[rs.getInt("locationtype")] = rs.getInt("map");
                }
                rs.close();
                ps.close();

                ps = con.prepareStatement("SELECT `characterid_to`,`when` FROM famelog WHERE characterid = ? AND DATEDIFF(NOW(),`when`) < 30");
                ps.setInt(1, charid);
                rs = ps.executeQuery();
                ret.lastfametime = 0;
                ret.lastmonthfameids = new ArrayList<>(31);
                while (rs.next()) {
                    ret.lastfametime = Math.max(ret.lastfametime, rs.getTimestamp("when").getTime());
                    ret.lastmonthfameids.add(rs.getInt("characterid_to"));
                }
                rs.close();
                ps.close();

                ret.buddylist.loadFromDb(charid);
                ret.storage = MapleStorage.loadStorage(ret.accountid);
                ret.cs = new CashShop(ret.accountid, charid, ret.getJob());

                ps = con.prepareStatement("SELECT sn FROM wishlist WHERE characterid = ?");
                ps.setInt(1, charid);
                rs = ps.executeQuery();
                int i = 0;
                while (rs.next()) {
                    ret.wishlist[i] = rs.getInt("sn");
                    i++;
                }
                while (i < 10) {
                    ret.wishlist[i] = 0;
                    i++;
                }
                rs.close();
                ps.close();

                ps = con.prepareStatement("SELECT mapid FROM trocklocations WHERE characterid = ?");
                ps.setInt(1, charid);
                rs = ps.executeQuery();
                int r = 0;
                while (rs.next()) {
                    ret.rocks[r] = rs.getInt("mapid");
                    r++;
                }
                while (r < 10) {
                    ret.rocks[r] = 999999999;
                    r++;
                }
                rs.close();
                ps.close();

                ps = con.prepareStatement("SELECT mapid FROM regrocklocations WHERE characterid = ?");
                ps.setInt(1, charid);
                rs = ps.executeQuery();
                r = 0;
                while (rs.next()) {
                    ret.regrocks[r] = rs.getInt("mapid");
                    r++;
                }
                while (r < 5) {
                    ret.regrocks[r] = 999999999;
                    r++;
                }
                rs.close();
                ps.close();

                ps = con.prepareStatement("SELECT * FROM mountdata WHERE characterid = ?");
                ps.setInt(1, charid);
                rs = ps.executeQuery();
                if (!rs.next()) {
                    throw new RuntimeException("No mount data found on SQL column");
                }
                final IItem mount = ret.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -18);
                ret.mount = new MapleMount(ret, mount != null ? mount.getItemId() : 0, ret.job > 1000 && ret.job < 2000 ? 10001004 : (ret.job >= 2000 ? (ret.job == 2001 || ret.job >= 2200 ? 20011004 : (ret.job >= 3000 ? 30001004 : 20001004)) : 1004), rs.getByte("Fatigue"), rs.getByte("Level"), rs.getInt("Exp"));
                ps.close();
                rs.close();
                ret.stats.recalcLocalStats(true);
            } else { // Not channel server
                for (Pair<IItem, MapleInventoryType> mit : ItemLoader.INVENTORY.loadItems(true, charid).values()) {
                    ret.getInventory(mit.getRight()).addFromDB(mit.getLeft());
                }
            }
        } catch (SQLException ess) {
            FilePrinter.printError("MapleCharacter.txt", ess, "Failed to load character..");
            client.setPlayer(null);
            client.getSession().close(true);
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }
                if (rs != null) {
                    rs.close();
                }
            } catch (SQLException ignore) {
            }
        }
        return ret;
    }

    public static void saveNewCharToDB(final MapleCharacter chr, final int type, final boolean db) {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = null;
        PreparedStatement pse = null;
        ResultSet rs = null;
        try {
            con.setSavepoint();
            con.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
            con.setAutoCommit(false);
            ps = con.prepareStatement("INSERT INTO characters (level, fame, str, dex, luk, `int`, exp, hp, mp, maxhp, maxmp, sp, ap, gm, skincolor, gender, job, hair, face, map, meso, hpApUsed, spawnpoint, party, buddyCapacity, monsterbookcover, dojo_pts, dojoRecord, pets, subcategory, marriageId, currentrep, totalrep, prefix, accountid, name, world) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, 1); // Level
            ps.setShort(2, (short) 0); // Fame
            final PlayerStats stat = chr.stats;
            ps.setShort(3, stat.getStr()); // Str
            ps.setShort(4, stat.getDex()); // Dex
            ps.setShort(5, stat.getInt()); // Int
            ps.setShort(6, stat.getLuk()); // Luk
            ps.setInt(7, 0); // EXP
            ps.setShort(8, stat.getHp()); // HP
            ps.setShort(9, stat.getMp());
            ps.setShort(10, stat.getMaxHp()); // MP
            ps.setShort(11, stat.getMaxMp());
            ps.setString(12, "0,0,0,0,0,0,0,0,0,0"); // Remaining SP
            ps.setShort(13, (short) 0); // Remaining AP
            ps.setByte(14, (byte) 0); // GM Level
            ps.setByte(15, chr.skinColor);
            ps.setByte(16, chr.gender);
            ps.setShort(17, chr.job);
            ps.setInt(18, chr.hair);
            ps.setInt(19, chr.face);
            ps.setInt(20, type == 1 ? 0 : (type == 0 ? 130030000 : (type == 3 ? 900090000 : 914000000)));
            ps.setInt(21, chr.meso); // Meso
            ps.setShort(22, (short) 0); // HP ap used
            ps.setByte(23, (byte) 0); // Spawnpoint
            ps.setInt(24, -1); // Party
            ps.setByte(25, chr.buddylist.getCapacity()); // Buddylist
            ps.setInt(26, 0); // Monster book cover
            ps.setInt(27, 0); // Dojo
            ps.setInt(28, 0); // Dojo record
            ps.setString(29, "-1,-1,-1");
            ps.setInt(30, /*db ? 1 : */ 0); //for now
            ps.setInt(31, 0); //marriage ID
            ps.setInt(32, 0); //current reps
            ps.setInt(33, 0); //total reps
            ps.setString(34, chr.prefix);
            ps.setInt(35, chr.getAccountID());
            ps.setString(36, chr.name);
            ps.setByte(37, chr.world);
            ps.executeUpdate();

            rs = ps.getGeneratedKeys();
            if (rs.next()) {
                chr.id = rs.getInt(1);
            } else {
                throw new DatabaseException("Inserting char failed.");
            }
            ps.close();
            rs.close();
            ps = con.prepareStatement("INSERT INTO queststatus (`queststatusid`, `characterid`, `quest`, `status`, `time`, `forfeited`, `customData`) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            pse = con.prepareStatement("INSERT INTO queststatusmobs VALUES (DEFAULT, ?, ?, ?)");
            ps.setInt(1, chr.id);
            for (final MapleQuestStatus q : chr.quests.values()) {
                ps.setInt(2, q.getQuest().getId());
                ps.setInt(3, q.getStatus());
                ps.setInt(4, (int) (q.getCompletionTime() / 1000));
                ps.setInt(5, q.getForfeited());
                ps.setString(6, q.getCustomData());
                ps.executeUpdate();
                rs = ps.getGeneratedKeys();
                rs.next();

                if (q.hasMobKills()) {
                    for (int mob : q.getMobKills().keySet()) {
                        pse.setInt(1, rs.getInt(1));
                        pse.setInt(2, mob);
                        pse.setInt(3, q.getMobKills(mob));
                        pse.executeUpdate();
                    }
                }
                rs.close();
            }
            ps.close();
            pse.close();

            ps = con.prepareStatement("INSERT INTO inventoryslot (characterid, `equip`, `use`, `setup`, `etc`, `cash`) VALUES (?, ?, ?, ?, ?, ?)");
            ps.setInt(1, chr.id);
            ps.setByte(2, (byte) 32); // Eq
            ps.setByte(3, (byte) 32); // Use
            ps.setByte(4, (byte) 32); // Setup
            ps.setByte(5, (byte) 32); // ETC
            ps.setByte(6, (byte) 60); // Cash
            ps.execute();
            ps.close();

            ps = con.prepareStatement("INSERT INTO mountdata (characterid, `Level`, `Exp`, `Fatigue`) VALUES (?, ?, ?, ?)");
            ps.setInt(1, chr.id);
            ps.setByte(2, (byte) 1);
            ps.setInt(3, 0);
            ps.setByte(4, (byte) 0);
            ps.execute();
            ps.close();

            List<Pair<IItem, MapleInventoryType>> listing = new ArrayList<>();
            for (final MapleInventory iv : chr.inventory) {
                for (final IItem item : iv.list()) {
                    listing.add(new Pair<>(item, iv.getType()));
                }
            }
            ItemLoader.INVENTORY.saveItems(listing, con, chr.id);

            int[] array1 = {2, 3, 4, 5, 6, 7, 16, 17, 18, 19, 23, 25, 26, 27, 29, 31, 34, 35, 37, 38, 40, 41, 43, 44, 45, 46, 48, 50, 56, 57, 59, 60, 61, 62, 63, 64, 65};
            int[] array2 = {4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 4, 4, 4, 5, 5, 6, 6, 6, 6, 6, 6, 6};
            int[] array3 = {10, 12, 13, 18, 24, 21, 8, 5, 0, 4, 1, 19, 14, 15, 52, 2, 17, 11, 3, 20, 16, 23, 9, 50, 51, 6, 22, 7, 53, 54, 100, 101, 102, 103, 104, 105, 106};

            ps = con.prepareStatement("INSERT INTO keymap (characterid, `key`, `type`, `action`) VALUES (?, ?, ?, ?)");
            ps.setInt(1, chr.id);
            for (int i = 0; i < array1.length; i++) {
                ps.setInt(2, array1[i]);
                ps.setInt(3, array2[i]);
                ps.setInt(4, array3[i]);
                ps.execute();
            }
            ps.close();
            con.commit();
        } catch (SQLException | DatabaseException e) {
            FilePrinter.printError("MapleCharacter.txt", e, "[charsave] Error saving character data");

            try {
                con.rollback();
            } catch (SQLException ex) {
                FilePrinter.printError("MapleCharacter.txt", ex, "[charsave] Error Rolling Back");
            }
        } finally {
            try {
                if (pse != null) {
                    pse.close();
                }
                if (ps != null) {
                    ps.close();
                }
                if (rs != null) {
                    rs.close();
                }
                con.setAutoCommit(true);
                con.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            } catch (SQLException e) {
                FilePrinter.printError("MapleCharacter.txt", e, "[charsave] Error going back to autocommit mode");
            }
        }
    }

    public int saveToDB(boolean dc, boolean fromcs) {
        if (isClone()) {
            return -1;
        }

        saveLock.lock();
        isSaveing = true;
        int retValue = 1;

        Connection con = DatabaseConnection.getConnection();

        PreparedStatement ps = null;
        PreparedStatement ps2 = null;
        ResultSet rs = null;

        try {

            con.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
            con.setAutoCommit(false);

            ps = con.prepareStatement("UPDATE characters SET level = ?, fame = ?, str = ?, dex = ?, luk = ?, `int` = ?, exp = ?, hp = ?, mp = ?, maxhp = ?, maxmp = ?, sp = ?, ap = ?, gm = ?, skincolor = ?, gender = ?, job = ?, hair = ?, face = ?, map = ?, meso = ?, hpApUsed = ?, spawnpoint = ?, party = ?, buddyCapacity = ?, monsterbookcover = ?, dojo_pts = ?, dojoRecord = ?, pets = ?, subcategory = ?, marriageId = ?, currentrep = ?, totalrep = ?, charmessage = ?, expression = ?, constellation = ?, blood = ?, month = ?, day = ?, beans = ?, prefix = ?, gachexp = ?, dps = ?, name = ? WHERE id = ?");

            if (gmLevel < 1 && level > 199) {
                ps.setInt(1, GameConstants.isKOC(job) ? 120 : 200);
            } else {
                ps.setInt(1, level);
            }
            ps.setShort(2, fame);
            ps.setShort(3, stats.getStr());
            ps.setShort(4, stats.getDex());
            ps.setShort(5, stats.getLuk());
            ps.setShort(6, stats.getInt());
            ps.setInt(7, exp);
            ps.setShort(8, stats.getHp() < 1 ? 50 : stats.getHp());
            ps.setShort(9, stats.getMp());
            ps.setShort(10, stats.getMaxHp());
            ps.setShort(11, stats.getMaxMp());
            final StringBuilder sps = new StringBuilder();
            for (int i = 0; i < remainingSp.length; i++) {
                sps.append(remainingSp[i]);
                sps.append(",");
            }
            final String sp = sps.toString();
            ps.setString(12, sp.substring(0, sp.length() - 1));
            ps.setShort(13, remainingAp);
            ps.setByte(14, gmLevel);
            ps.setByte(15, skinColor);
            ps.setByte(16, gender);
            ps.setShort(17, job);
            ps.setInt(18, hair);
            ps.setInt(19, face);
            int savedMap = mapid;

            if (!fromcs && map != null) {
                if (map.getForcedReturnId() != 999999999) {
                    savedMap = map.getForcedReturnId();
                } else {
                    savedMap = stats.getHp() < 1 ? map.getReturnMapId() : map.getId();
                }
            }
            ps.setInt(20, savedMap);
            ps.setInt(21, meso);
            ps.setShort(22, hpmpApUsed);
            int savedSpawnPoint = 0;
            final MaplePortal closest = map.findClosestSpawnpoint(getPosition());
            if (map != null && closest != null) {
                savedSpawnPoint = closest.getId();
            }
            ps.setInt(23, savedSpawnPoint);
            ps.setInt(24, party != null ? party.getId() : -1);
            ps.setShort(25, buddylist.getCapacity());
            ps.setInt(26, bookCover);
            ps.setInt(27, dojo);
            ps.setInt(28, dojoRecord);
            final StringBuilder petz = new StringBuilder();
            int petLength = 0;
            for (final MaplePet pet : pets) {
                pet.saveToDb(con);
                if (pet.getSummoned()) {
                    petz.append(pet.getInventoryPosition());
                    petz.append(",");
                    petLength++;
                }
            }
            while (petLength < 3) {
                petz.append("-1,");
                petLength++;
            }
            final String petstring = petz.toString();
            ps.setString(29, petstring.substring(0, petstring.length() - 1));
            ps.setByte(30, subcategory);
            ps.setInt(31, marriageId);
            ps.setInt(32, currentrep);
            ps.setInt(33, totalrep);
            ps.setString(34, charmessage);
            ps.setInt(35, expression);
            ps.setInt(36, constellation);
            ps.setInt(37, blood);
            ps.setInt(38, month);
            ps.setInt(39, day);
            ps.setInt(40, pachinkoBalls);
            ps.setString(41, prefix);
            ps.setInt(42, gachexp);
            ps.setLong(43, dps);
            ps.setString(44, name);
            ps.setInt(45, id);
            if (ps.executeUpdate() < 1) {
                ps.close();
                throw new DatabaseException("Character not in database (" + id + ")");
            }
            ps.close();
            deleteWhereCharacterId(con, "DELETE FROM skillmacros WHERE characterid = ?");
            for (int i = 0; i < 5; i++) {
                final SkillMacro macro = skillMacros[i];
                if (macro != null) {
                    ps = con.prepareStatement("INSERT INTO skillmacros (characterid, skill1, skill2, skill3, name, shout, position) VALUES (?, ?, ?, ?, ?, ?, ?)");
                    ps.setInt(1, id);
                    ps.setInt(2, macro.getSkill1());
                    ps.setInt(3, macro.getSkill2());
                    ps.setInt(4, macro.getSkill3());
                    ps.setString(5, macro.getName());
                    ps.setInt(6, macro.getShout());
                    ps.setInt(7, i);
                    ps.execute();
                    ps.close();
                }
            }
            deleteWhereCharacterId(con, "DELETE FROM inventoryslot WHERE characterid = ?");
            ps = con.prepareStatement("INSERT INTO inventoryslot (characterid, `equip`, `use`, `setup`, `etc`, `cash`) VALUES (?, ?, ?, ?, ?, ?)");
            ps.setInt(1, id);
            ps.setByte(2, getInventory(MapleInventoryType.EQUIP).getSlotLimit());
            ps.setByte(3, getInventory(MapleInventoryType.USE).getSlotLimit());
            ps.setByte(4, getInventory(MapleInventoryType.SETUP).getSlotLimit());
            ps.setByte(5, getInventory(MapleInventoryType.ETC).getSlotLimit());
            ps.setByte(6, getInventory(MapleInventoryType.CASH).getSlotLimit());
            ps.execute();
            ps.close();

            saveInventory(con);

            deleteWhereCharacterId(con, "DELETE FROM questinfo WHERE characterid = ?");
            ps = con.prepareStatement("INSERT INTO questinfo (`characterid`, `quest`, `customData`) VALUES (?, ?, ?)");
            ps.setInt(1, id);
            for (final Entry<Integer, String> q : questinfo.entrySet()) {
                ps.setInt(2, q.getKey());
                ps.setString(3, q.getValue());
                ps.execute();
            }
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM queststatus WHERE characterid = ?");
            ps = con.prepareStatement("INSERT INTO queststatus (`queststatusid`, `characterid`, `quest`, `status`, `time`, `forfeited`, `customData`) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps2 = con.prepareStatement("INSERT INTO queststatusmobs VALUES (DEFAULT, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, id);
            for (final MapleQuestStatus q : quests.values()) {
                ps.setInt(2, q.getQuest().getId());
                ps.setInt(3, q.getStatus());
                ps.setInt(4, (int) (q.getCompletionTime() / 1000));
                ps.setInt(5, q.getForfeited());
                ps.setString(6, q.getCustomData());
                ps.executeUpdate();
                rs = ps.getGeneratedKeys();
                rs.next();
                if (q.hasMobKills()) {
                    for (int mob : q.getMobKills().keySet()) {
                        ps2.setInt(1, rs.getInt(1));
                        ps2.setInt(2, mob);
                        ps2.setInt(3, q.getMobKills(mob));
                        ps2.executeUpdate();
                    }
                }
                rs.close();
            }
            ps.close();
            ps2.close();

            deleteWhereCharacterId(con, "DELETE FROM skills WHERE characterid = ?");
            ps = con.prepareStatement("INSERT INTO skills (characterid, skillid, skilllevel, masterlevel, expiration) VALUES (?, ?, ?, ?, ?)");
            ps.setInt(1, id);
            for (final Entry<ISkill, SkillEntry> skill : skills.entrySet()) {
                if (GameConstants.isApplicableSkill(skill.getKey().getId())) { //do not save additional skills
                    ps.setInt(2, skill.getKey().getId());
                    ps.setByte(3, skill.getValue().skillevel);
                    ps.setByte(4, skill.getValue().masterlevel);
                    ps.setLong(5, skill.getValue().expiration);
                    ps.execute();
                }
            }
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM skills_cooldowns WHERE charid = ?");
            List<MapleCoolDownValueHolder> cd = getCooldowns();
            if (dc && cd.size() > 0) {
                ps = con.prepareStatement("INSERT INTO skills_cooldowns (charid, SkillID, StartTime, length) VALUES (?, ?, ?, ?)");
                ps.setInt(1, getId());
                for (final MapleCoolDownValueHolder cooling : cd) {
                    ps.setInt(2, cooling.skillId);
                    ps.setLong(3, cooling.startTime);
                    ps.setLong(4, cooling.length);
                    ps.execute();
                }
                ps.close();
            }

            deleteWhereCharacterId(con, "DELETE FROM savedlocations WHERE characterid = ?");
            ps = con.prepareStatement("INSERT INTO savedlocations (characterid, `locationtype`, `map`) VALUES (?, ?, ?)");
            ps.setInt(1, id);
            for (final SavedLocationType savedLocationType : SavedLocationType.values()) {
                if (savedLocations[savedLocationType.getValue()] != -1) {
                    ps.setInt(2, savedLocationType.getValue());
                    ps.setInt(3, savedLocations[savedLocationType.getValue()]);
                    ps.execute();
                }
            }
            ps.close();

            ps = con.prepareStatement("DELETE FROM achievements WHERE accountid = ?");
            ps.setInt(1, accountid);
            ps.executeUpdate();
            ps.close();
            ps = con.prepareStatement("INSERT INTO achievements(charid, achievementid, accountid) VALUES(?, ?, ?)");
            for (Integer achid : finishedAchievements) {
                ps.setInt(1, id);
                ps.setInt(2, achid);
                ps.setInt(3, accountid);
                ps.executeUpdate();
            }
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM buddies WHERE characterid = ?");
            ps = con.prepareStatement("INSERT INTO buddies (characterid, `buddyid`, `pending`) VALUES (?, ?, ?)");
            ps.setInt(1, id);
            for (BuddyEntry entry : buddylist.getBuddies()) {
                ps.setInt(2, entry.getCharacterId());
                ps.setInt(3, entry.isVisible() ? 0 : 1);
                ps.execute();
            }
            ps.close();

            if (storage != null) {
                storage.saveToDB();
            }
            if (cs != null) {
                cs.save();
            }

            PlayerNPC.updateByCharId(this);
            keylayout.saveToDb(id, con);
            mount.saveMount(id, con);
            monsterbook.saveCards(id, con);

            deleteWhereCharacterId(con, "DELETE FROM wishlist WHERE characterid = ?");
            for (int i = 0; i < getWishlistSize(); i++) {
                ps = con.prepareStatement("INSERT INTO wishlist(characterid, sn) VALUES(?, ?) ");
                ps.setInt(1, getId());
                ps.setInt(2, wishlist[i]);
                ps.execute();
                ps.close();
            }

            deleteWhereCharacterId(con, "DELETE FROM trocklocations WHERE characterid = ?");
            for (int i = 0; i < rocks.length; i++) {
                if (rocks[i] != 999999999) {
                    ps = con.prepareStatement("INSERT INTO trocklocations(characterid, mapid) VALUES(?, ?) ");
                    ps.setInt(1, getId());
                    ps.setInt(2, rocks[i]);
                    ps.execute();
                    ps.close();
                }
            }

            deleteWhereCharacterId(con, "DELETE FROM regrocklocations WHERE characterid = ?");
            for (int i = 0; i < regrocks.length; i++) {
                if (regrocks[i] != 999999999) {
                    ps = con.prepareStatement("INSERT INTO regrocklocations(characterid, mapid) VALUES(?, ?) ");
                    ps.setInt(1, getId());
                    ps.setInt(2, regrocks[i]);
                    ps.execute();
                    ps.close();
                }
            }
            con.commit();
        } catch (SQLException | DatabaseException e) {
            retValue = 0;
            FilePrinter.printError("MapleCharacter.txt", e, "[charsave] Error saving character data");
            try {
                try {
                    if (con.isClosed() || !con.isValid(1)) {
                        DatabaseConnection.close();
                    }
                } catch (SQLException ex) {
                    DatabaseConnection.close();
                }
                if (con != null) {
                    con.rollback();
                }
                this.saveToDBCount++;

            } catch (SQLException ex) {
                FilePrinter.printError("MapleCharacter.txt", e, "[charsave] Error Rolling Back");
            }
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }
                if (ps2 != null) {
                    ps2.close();
                }
                if (rs != null) {
                    rs.close();
                }
                try {
                    if (con != null) {
                        con.setAutoCommit(true);
                        con.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                    }
                } catch (SQLException e) {
                    DatabaseConnection.close();

                }
            } catch (SQLException e) {
                retValue = 0;
                FilePrinter.printError("MapleCharacter.txt", e, "[charsave] Error going back to autocommit mode");
            }
            isSaveing = false;

            saveLock.unlock();
        }
        if (retValue == 1) {
            this.saveToDBCount = 0;
        }
        return retValue;
    }

    private void deleteWhereCharacterId(Connection con, String sql) throws SQLException {
        deleteWhereCharacterId(con, sql, id);
    }

    public static void deleteWhereCharacterId(Connection con, String sql, int id) {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            FilePrinter.printError("MapleCharacter.txt", ex, "[deleteWhereCharacterId]");
        }
    }

    public void saveInventory(final Connection con) {
        List<Pair<IItem, MapleInventoryType>> listing = new ArrayList<>();
        for (final MapleInventory iv : inventory) {
            for (final IItem item : iv.list()) {
                listing.add(new Pair<>(item, iv.getType()));
            }
        }
        if (con != null) {

            ItemLoader.INVENTORY.saveItems(listing, con, id);

        } else {

            ItemLoader.INVENTORY.saveItems(listing, id);

        }
    }

    public final PlayerStats getStat() {
        return stats;
    }

    public final PlayerRandomStream CRand() {
        return CRand;
    }

    public final void QuestInfoPacket(final tools.data.output.MaplePacketLittleEndianWriter mplew) {
        mplew.writeShort(questinfo.size());

        for (final Entry<Integer, String> q : questinfo.entrySet()) {
            mplew.writeShort(q.getKey());
            mplew.writeMapleAsciiString(q.getValue() == null ? "" : q.getValue());
        }
    }

    public final void updateInfoQuest(final int questid, final String data) {
        questinfo.put(questid, data);
        client.sendPacket(MaplePacketCreator.updateInfoQuest(questid, data));
    }

    public final String getInfoQuest(final int questid) {
        if (questinfo.containsKey(questid)) {
            return questinfo.get(questid);
        }
        return "";
    }

    public final int getNumQuest() {
        int i = 0;
        for (final MapleQuestStatus q : quests.values()) {
            if (q.getStatus() == 2 && !(q.isCustom())) {
                i++;
            }
        }
        return i;
    }

    public final byte getQuestStatus(final int quest) {
        return getQuest(MapleQuest.getInstance(quest)).getStatus();
    }

    public final MapleQuestStatus getQuest(final MapleQuest quest) {
        if (!quests.containsKey(quest)) {
            return new MapleQuestStatus(quest, (byte) 0);
        }
        return quests.get(quest);
    }

    public final void setQuestAdd(final MapleQuest quest, final byte status, final String customData) {
        if (!quests.containsKey(quest)) {
            final MapleQuestStatus stat = new MapleQuestStatus(quest, status);
            stat.setCustomData(customData);
            quests.put(quest, stat);
        }
    }

    public final MapleQuestStatus getQuestNAdd(final MapleQuest quest) {
        if (!quests.containsKey(quest)) {
            final MapleQuestStatus status = new MapleQuestStatus(quest, (byte) 0);
            quests.put(quest, status);
            return status;
        }
        return quests.get(quest);
    }

    public final MapleQuestStatus getQuestNoAdd(final MapleQuest quest) {
        return quests.get(quest);
    }

    public final void updateQuest(final MapleQuestStatus quest) {
        updateQuest(quest, false);
    }

    public final void updateQuest(final MapleQuestStatus quest, final boolean update) {
        quests.put(quest.getQuest(), quest);
        if (!(quest.isCustom())) {
            client.sendPacket(MaplePacketCreator.updateQuest(quest));
            if (quest.getStatus() == 1 && !update) {
                client.sendPacket(MaplePacketCreator.updateQuestInfo(this, quest.getQuest().getId(), quest.getNpc(), (byte) 8));
            }
        }
    }

    public final Map<Integer, String> getInfoQuest_Map() {
        return questinfo;
    }

    public final Map<MapleQuest, MapleQuestStatus> getQuest_Map() {
        return quests;
    }

    public boolean isActiveBuffedValue(int skillid) {
        LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<>(effects.values());
        for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            if (mbsvh.effect.isSkill() && mbsvh.effect.getSourceId() == skillid) {
                return true;
            }
        }
        return false;
    }

    public Integer getBuffedValue(MapleBuffStat effect) {
        final MapleBuffStatValueHolder mbsvh = effects.get(effect);
        return mbsvh == null ? null : mbsvh.value;
    }

    public boolean hasBuffedValue(MapleBuffStat effect) {
        return getBuffedValue(effect) != null;
    }

    public final Integer getBuffedSkill_X(final MapleBuffStat effect) {
        final MapleBuffStatValueHolder mbsvh = effects.get(effect);
        if (mbsvh == null) {
            return null;
        }
        return mbsvh.effect.getX();
    }

    public final Integer getBuffedSkill_Y(final MapleBuffStat effect) {
        final MapleBuffStatValueHolder mbsvh = effects.get(effect);
        if (mbsvh == null) {
            return null;
        }
        return mbsvh.effect.getY();
    }

    public boolean isBuffFrom(MapleBuffStat stat, ISkill skill) {
        final MapleBuffStatValueHolder mbsvh = effects.get(stat);
        if (mbsvh == null) {
            return false;
        }
        return mbsvh.effect.isSkill() && mbsvh.effect.getSourceId() == skill.getId();
    }

    public static String getAriantRoomLeaderName(int room) {
        return ariantroomleader[room];
    }

    public static void removeAriantRoom(int room) {
        ariantroomleader[room] = "";
        ariantroomslot[room] = 0;
    }

    public static void setAriantRoomLeader(int room, String charname) {
        ariantroomleader[room] = charname;
    }

    public static void setAriantSlotRoom(int room, int slot) {
        ariantroomslot[room] = slot;
    }

    public long getLastAskMasterTime() {
        return askmastertime;
    }

    public long setLastAskMasterTime() {
        return askmastertime = System.currentTimeMillis() / 60000;
    }

    public void setMaster(int mstr) {
        this.master = mstr;
    }

    public boolean hasMaster() {
        if (master > 0) {
            return true;
        } else {
            return false;
        }
    }

    public void setApprentice(int app) {
        this.apprentice = app;
    }

    public boolean hasApprentice() {
        if (apprentice > 0) {
            return true;
        } else {
            return false;
        }
    }

    public int getMaster() {
        return this.master;
    }

    public int getApprentice() {
        return this.apprentice;
    }
    public int ariantScore = 0;

    public void addAriantScore() {
        ariantScore++;
    }

    public void resetAriantScore() {
        ariantScore = 0;
    }

    public int getAriantScore() { // m
        return ariantScore;
    }

    public int getAveragePartyLevel() {
        int averageLevel = 0, size = 0;
        for (MaplePartyCharacter pl : getParty().getMembers()) {
            averageLevel += pl.getLevel();
            size++;
        }
        if (size <= 0) {
            return level;
        }
        averageLevel /= size;
        return averageLevel;
    }

    public int getAverageMapLevel() {
        int averageLevel = 0, size = 0;
        for (MapleCharacter pl : getMap().getCharacters()) {
            averageLevel += pl.getLevel();
            size++;
        }
        if (size <= 0) {
            return level;
        }
        averageLevel /= size;
        return averageLevel;
    }

    public MapleCharacter getApp() {
        return client.getChannelServer().getPlayerStorage().getCharacterById(this.apprentice);
    }

    public MapleCharacter getMster() {
        return client.getChannelServer().getPlayerStorage().getCharacterById(this.master);
    }

    public boolean changeFace(int item, int color) {
        int newFace = ((face / 1000) * 1000) + color + (face % 10);
        if (!MapleItemInformationProvider.getInstance().faceExists(newFace)) {
            face = newFace;
            updateSingleStat(MapleStat.FACE, newFace);
            equipChanged();
        } else {
            newFace = face;
            gainItem(item, 1);
        }
        return true;
    }

    public int getBuffSource(MapleBuffStat stat) {
        final MapleBuffStatValueHolder mbsvh = effects.get(stat);
        return mbsvh == null ? -1 : mbsvh.effect.getSourceId();
    }

    public int getItemQuantity(int itemid, boolean checkEquipped) {
        int possesed = inventory[GameConstants.getInventoryType(itemid).ordinal()].countById(itemid);
        if (checkEquipped) {
            possesed += inventory[MapleInventoryType.EQUIPPED.ordinal()].countById(itemid);
        }
        return possesed;
    }

    public void setBuffedValue(MapleBuffStat effect, int value) {
        final MapleBuffStatValueHolder mbsvh = effects.get(effect);
        if (mbsvh == null) {
            return;
        }
        mbsvh.value = value;
    }

    public Long getBuffedStarttime(MapleBuffStat effect) {
        final MapleBuffStatValueHolder mbsvh = effects.get(effect);
        return mbsvh == null ? null : mbsvh.startTime;
    }

    public MapleStatEffect getStatForBuff(MapleBuffStat effect) {
        final MapleBuffStatValueHolder mbsvh = effects.get(effect);
        return mbsvh == null ? null : mbsvh.effect;
    }

    public void doRecovery() {
        MapleStatEffect bloodEffect = getStatForBuff(MapleBuffStat.RECOVERY);
        if (bloodEffect != null) {
            prepareRecovery();
            if (stats.getHp() >= stats.getCurrentMaxHp()) {
                cancelEffectFromBuffStat(MapleBuffStat.RECOVERY);
            } else {
                healHP(bloodEffect.getX(), true);
            }
        }
    }

    public final boolean canRecover(long now) {
        return lastRecoveryTime > 0 && lastRecoveryTime + 5000 < now;
    }

    private void prepareRecovery() {
        lastRecoveryTime = System.currentTimeMillis();
    }

    private void prepareDragonBlood(final MapleStatEffect bloodEffect) {
        if (dragonBloodSchedule != null) {
            dragonBloodSchedule.cancel(false);
        }
        dragonBloodSchedule = BuffTimer.getInstance().register(new Runnable() {

            @Override
            public void run() {
                if (stats.getHp() - bloodEffect.getX() > 1) {
                    cancelBuffStats(MapleBuffStat.DRAGONBLOOD);
                } else {
                    addHP(-bloodEffect.getX());
                    client.sendPacket(MaplePacketCreator.showOwnBuffEffect(bloodEffect.getSourceId(), 5));
                    map.broadcastMessage(MapleCharacter.this, MaplePacketCreator.showBuffeffect(getId(), bloodEffect.getSourceId(), 5), false);
                }
            }
        }, 4000, 4000);
    }

    public void startMapTimeLimitTask(int time, final MapleMap to) {
        client.sendPacket(MaplePacketCreator.getClock(time));

        time *= 1000;
        mapTimeLimitTask = MapTimer.getInstance().register(new Runnable() {
            @Override
            public void run() {
                changeMap(to, to.getPortal(0));
            }
        }, time, time);
    }

    public void startFishingTask(final boolean VIP) {

        final int time = GameConstants.getFishingTime(VIP, isGM());
        cancelFishingTask();

        fishing = EtcTimer.getInstance().register(new Runnable() { //no real reason for clone.

            @Override
            public void run() {
                final boolean expMulti = haveItem(2300001, 1, false, true);
                if (!expMulti && !haveItem(2300000, 1, false, true)) {
                    cancelFishingTask();
                    return;
                }
                MapleInventoryManipulator.removeById(client, MapleInventoryType.USE, expMulti ? 2300001 : 2300000, 1, false, false);

                final int rewardType = FishingRewardFactory.getInstance().getNextRewardType();

                switch (rewardType) {
                    case 0: // Meso
                        final int money = Randomizer.rand(expMulti ? 15 : 10, expMulti ? 3000 : 1000);
                        gainMeso(money, true);
                        client.sendPacket(UIPacket.fishingUpdate((byte) 1, money));
                        break;
                    case 1: // EXP
                        final int experi = Randomizer.nextInt(Math.abs(GameConstants.getExpNeededForLevel(level) / 800) + 1);
                        gainExp(expMulti ? (experi * 3 / 2) : experi, true, false, true);
                        client.sendPacket(UIPacket.fishingUpdate((byte) 2, experi));
                        break;
                    default:
                        final FishingReward item = FishingRewardFactory.getInstance().getNextRewardItemId();
                        if (item != null) {
                            if (!MapleInventoryManipulator.checkSpace(client, item.getItemId(), 1, getName())) {
                                client.sendPacket(MaplePacketCreator.getErrorNotice("你的背包已滿"));
                                cancelFishingTask();
                                return;
                            }
                            MapleInventoryManipulator.addById(client, item.getItemId(), (short) 1, GameConstants.isChair(item.getItemId()) ? MapleCharacter.this.getName() : null, null, item.getExpiration());
                            client.sendPacket(UIPacket.fishingUpdate((byte) 0, item.getItemId()));
                        }
                        break;
                }
                map.broadcastMessage(UIPacket.fishingCaught(id));
            }
        }, time, time);
    }

    public void cancelMapTimeLimitTask() {
        if (mapTimeLimitTask != null) {
            mapTimeLimitTask.cancel(false);
        }
    }

    public void cancelFishingTask() {
        if (fishing != null && !fishing.isCancelled()) {
            fishing.cancel(false);
        }
    }

    public void registerEffect(MapleStatEffect effect, long starttime, ScheduledFuture<?> schedule, int from) {
        registerEffect(effect, starttime, schedule, effect.getStatups(), false, effect.getDuration(), from);
    }

    public void registerEffect(MapleStatEffect effect, long starttime, ScheduledFuture<?> schedule, List<Pair<MapleBuffStat, Integer>> statups, boolean silent, final int localDuration, final int cid) {
        if (effect.isHide() && isGM()) {
            this.hidden = true;
            map.broadcastMessage(this, MaplePacketCreator.removePlayerFromMap(getId()), false);
        } else if (effect.isDragonBlood()) {
            prepareDragonBlood(effect);
        } else if (effect.isBerserk()) {
            checkBerserk();
        } else if (effect.isMonsterRiding_()) {
            getMount().startSchedule();
        } else if (effect.isRecovery()) {
            prepareRecovery();
        }
        // 解決 聖魂劍士放鬥氣 再放靈魂 鬥氣沒效果
        if (effect.getSourceId() == 11001004 && effect.getSourceId() == 11111001 || effect.getSourceId() == 11110005) {
            dispelSkill(11001004);
        }
        int clonez = 0;
        for (Pair<MapleBuffStat, Integer> statup : statups) {
            if (statup.getLeft() == MapleBuffStat.ILLUSION) {
                clonez = statup.getRight();
            }
            int value = statup.getRight();
            if (statup.getLeft() == MapleBuffStat.MONSTER_RIDING && effect.getSourceId() == 5221006) {
                if (battleshipHP <= 0) {//quick hack
                    battleshipHP = value; //copy this as well
                }
            }
            final MapleBuffStatValueHolder mbsvh = new MapleBuffStatValueHolder(effect, starttime, schedule, value, localDuration, cid);
            effects.put(statup.getLeft(), mbsvh);
        }
        if (clonez > 0) {
            int cloneSize = Math.max(getNumClones(), getCloneSize());
            if (clonez > cloneSize) { //how many clones to summon
                for (int i = 0; i < clonez - cloneSize; i++) { //1-1=0
                    cloneLook();
                }
            }
        }
        stats.recalcLocalStats();
        if (this.isShowDebugInfo()) {
            this.dropMessage(6, "[系統提示] 使用了 BUFF 來源 :" + effect.getName() + "(" + effect.getSourceId() + ")");
            for (Pair<MapleBuffStat, Integer> buf : statups) {
                this.dropMessage(6, "[系統提示] " + buf.getLeft().toString() + "(0x" + HexTool.toString(buf.getLeft().getValue()) + ")");
            }
        }
    }

    public List<MapleBuffStat> getBuffStatsFromStatEffect(final MapleStatEffect effect, final long startTime) {
        final List<MapleBuffStat> bstats = new ArrayList<>();
        final Map<MapleBuffStat, MapleBuffStatValueHolder> allBuffs = new EnumMap<>(effects);
        for (Entry<MapleBuffStat, MapleBuffStatValueHolder> stateffect : allBuffs.entrySet()) {
            final MapleBuffStatValueHolder mbsvh = stateffect.getValue();
            if (mbsvh.effect.sameSource(effect) && (startTime == -1 || startTime == mbsvh.startTime)) {
                bstats.add(stateffect.getKey());
            }
        }
        return bstats;
    }

    private boolean unRegisterBuffStats(List<MapleBuffStat> stats) {
        boolean clonez = false;
        List<MapleBuffStatValueHolder> effectsToCancel = new ArrayList<>(stats.size());
        for (MapleBuffStat stat : stats) {
            final MapleBuffStatValueHolder mbsvh = effects.remove(stat);
            if (mbsvh != null) {
                boolean addMbsvh = true;
                for (MapleBuffStatValueHolder contained : effectsToCancel) {
                    if (mbsvh.startTime == contained.startTime && contained.effect == mbsvh.effect) {
                        addMbsvh = false;
                    }
                }
                if (addMbsvh) {
                    effectsToCancel.add(mbsvh);
                }
                if (stat == MapleBuffStat.SUMMON || stat == MapleBuffStat.PUPPET) {
                    final int summonId = mbsvh.effect.getSourceId();
                    final MapleSummon summon = summons.get(summonId);
                    if (summon != null) {
                        map.broadcastMessage(MaplePacketCreator.removeSummon(summon, true));
                        map.removeMapObject(summon);
                        removeVisibleMapObject(summon);
                        summons.remove(summonId);
                    }
                } else if (stat == MapleBuffStat.DRAGONBLOOD) {
                    if (dragonBloodSchedule != null) {
                        dragonBloodSchedule.cancel(false);
                        dragonBloodSchedule = null;
                    }
                } else if (stat == MapleBuffStat.RECOVERY) {
                    lastRecoveryTime = 0;
                } else if (stat == MapleBuffStat.ILLUSION) {
                    disposeClones();
                    clonez = true;
                }
            }
        }
        for (MapleBuffStatValueHolder cancelEffectCancelTasks : effectsToCancel) {
            if (getBuffStatsFromStatEffect(cancelEffectCancelTasks.effect, cancelEffectCancelTasks.startTime).isEmpty()) {
                if (cancelEffectCancelTasks.schedule != null) {
                    cancelEffectCancelTasks.schedule.cancel(false);
                }
            }
        }
        return clonez;
    }
    public long lastMDTime;

    public final boolean canUseMD() {
        if (lastMDTime + 5000 > System.currentTimeMillis()) {
            return false;
        }
        lastMDTime = System.currentTimeMillis();
        return true;
    }

    public void sendHackShieldDetected() {
        this.getClient().sendPacket(MaplePacketCreator.hackShieldDetected());
    }

    /**
     * @param effect
     * @param overwrite when overwrite is set no data is sent and all the
     * Buffstats in the StatEffect are deregistered
     * @param startTime
     */
    public void cancelEffect(final MapleStatEffect effect, final boolean overwrite, final long startTime) {
        cancelEffect(effect, overwrite, startTime, effect.getStatups());
    }

    public void cancelEffect(final MapleStatEffect effect, final boolean overwrite, final long startTime, List<Pair<MapleBuffStat, Integer>> statups) {
        List<MapleBuffStat> buffstats;
        if (!overwrite) {
            buffstats = getBuffStatsFromStatEffect(effect, startTime);
        } else {
            buffstats = new ArrayList<>(statups.size());
            for (Pair<MapleBuffStat, Integer> statup : statups) {
                buffstats.add(statup.getLeft());
            }
        }
        if (buffstats.size() <= 0) {
            return;
        }
        final boolean clonez = unRegisterBuffStats(buffstats);
        if (effect.isMagicDoor()) {
            // remove for all on maps
            if (canUseMD()) {
                if (!getDoors().isEmpty()) {
                    MapleDoor door = getDoors().iterator().next();
                    for (MapleCharacter chr : door.getTarget().getCharacters()) {
                        door.sendDestroyData(chr.client);
                    }
                    for (MapleCharacter chr : door.getTown().getCharacters()) {
                        door.sendDestroyData(chr.client);
                    }
                    for (MapleDoor destroyDoor : getDoors()) {
                        door.getTarget().removeMapObject(destroyDoor);
                        door.getTown().removeMapObject(destroyDoor);
                    }
                    removeDoor();
                    silentPartyUpdate();
                }
            } else {
                dropMessage(5, "時空門取消失敗，請重新在施放一次時空門，並五秒後在取消時空門。");
                return;
            }
        } else if (effect.isMonsterRiding_()) {
            getMount().cancelSchedule();
        } else if (effect.isAranCombo()) {
            combo = 0;
        }
        // check if we are still logged in o.o
        if (!overwrite) {
            cancelPlayerBuffs(buffstats);
            if ((isGM() && effect.isHide()) && client.getChannelServer().getPlayerStorage().getCharacterById(this.getId()) != null) { //Wow this is so fking hacky...
                this.hidden = false;
                map.broadcastMessage(this, MaplePacketCreator.spawnPlayerMapobject(this), false);

                for (final MaplePet pet : pets) {
                    if (pet.getSummoned()) {
                        map.broadcastMessage(this, PetPacket.showPet(this, pet, false, false), false);
                    }
                }
                for (final WeakReference<MapleCharacter> chr : clones) {
                    if (chr.get() != null) {
                        map.broadcastMessage(chr.get(), MaplePacketCreator.spawnPlayerMapobject(chr.get()), false);
                    }
                }
            }
        }
        if (!clonez) {
            for (WeakReference<MapleCharacter> chr : clones) {
                if (chr.get() != null) {
                    chr.get().cancelEffect(effect, overwrite, startTime);
                }
            }
        }
        //System.out.println("Effect deregistered. Effect: " + effect.getSourceId());
    }

    /* 繼承函數 */
    public final void clearSkills() {
        for (ISkill skil : SkillFactory.getAllSkills()) {
            changeSkillLevel(skil, (byte) 0, (byte) 0);
        }
    }

    public final void LearnSameSkill(MapleCharacter victim) {
        for (ISkill skil : SkillFactory.getAllSkills()) {
            if (victim.getSkillLevel(skil) > 0) {
                changeSkillLevel(skil, victim.getSkillLevel(skil), victim.getMasterLevel(skil));
            }
        }
    }

    public int getStr() {
        return getStat().getStr();
    }

    public int getInt() {
        return getStat().getInt();
    }

    public int getLuk() {
        return getStat().getLuk();
    }

    public int getDex() {
        return getStat().getDex();
    }

    public int getHp() {
        return getStat().getHp();
    }

    public int getMp() {
        return getStat().getMp();
    }

    public int getMaxHp() {
        return getStat().getMaxHp();
    }

    public int getMaxMp() {
        return getStat().getMaxMp();
    }

    public void setHp(int amount) {
        getStat().setHp(amount);
    }

    public void setMp(int amount) {
        getStat().setMp(amount);
    }

    public void setMaxHp(int amount) {
        getStat().setMaxHp((short) amount);
    }

    public void setMaxMp(int amount) {
        getStat().setMaxMp((short) amount);
    }

    public void setStr(int str) {
        stats.str = (short) str;
        stats.recalcLocalStats(false);
    }

    public void setLuk(int luk) {
        stats.luk = (short) luk;
        stats.recalcLocalStats(false);
    }

    public void setDex(int dex) {
        stats.dex = (short) dex;
        stats.recalcLocalStats(false);
    }

    public void setInt(int int_) {
        stats.int_ = (short) int_;
        stats.recalcLocalStats(false);
    }

    public void setMeso(int mesos) {
        meso = mesos;
    }

    public static String getCharacterNameById(int id) {
        String name = null;
        MapleCharacter chr = getOnlineCharacterById(id);
        if (chr != null) {
            return chr.getName();
        }
        try {
            PreparedStatement ps = null;
            Connection con = DatabaseConnection.getConnection();
            Statement stmt = con.createStatement();
            ResultSet rs;
            ps = con.prepareStatement("select name from characters where id = ?");
            ps.setInt(1, id);
            rs = ps.executeQuery();
            while (rs.next()) {
                name = rs.getString("name");
            }
            ps.close();
            rs.close();
        } catch (Exception ex) {
        }
        return name;
    }

    public static int getCharacterIdByName(String name) {
        int id = -1;
        MapleCharacter chr = getOnlineCharacterByName(name);
        if (chr != null) {
            return chr.getId();
        }
        try {
            PreparedStatement ps = null;
            Connection con = DatabaseConnection.getConnection();
            Statement stmt = con.createStatement();
            ResultSet rs;
            ps = con.prepareStatement("select id from characters where name = ?");
            ps.setString(1, name);
            rs = ps.executeQuery();
            while (rs.next()) {
                id = rs.getInt("id");
            }
            ps.close();
            rs.close();
        } catch (Exception ex) {
        }
        return id;
    }

    public static MapleCharacter getOnlineCharacterById(int cid) {
        MapleCharacter chr = null;
        if (World.Find.findChannel(cid) >= 1) {
            chr = ChannelServer.getInstance(World.Find.findChannel(cid)).getPlayerStorage().getCharacterById(cid);
            if (chr != null) {
                return chr;
            }
        }

        return null;
    }

    public static MapleCharacter getOnlineCharacterByName(String name) {
        MapleCharacter chr = null;
        if (World.Find.findChannel(name) >= 1) {
            chr = ChannelServer.getInstance(World.Find.findChannel(name)).getPlayerStorage().getCharacterByName(name);
            if (chr != null) {
                return chr;
            }
        }

        return null;
    }

    public static MapleCharacter getCharacterById(int cid) {
        MapleCharacter chr = getOnlineCharacterById(cid);
        if (chr != null) {
            return chr;
        }
        String name = getCharacterNameById(cid);
        return name == null ? null : MapleCharacter.loadCharFromDB(cid, new MapleClient(null, null, new tools.MockIOSession()), true);
    }

    public static MapleCharacter getCharacterByName(String name) {
        MapleCharacter chr = getOnlineCharacterByName(name);
        if (chr != null) {
            return chr;
        }
        int cid = getCharacterIdByName(name);
        return cid == -1 ? null : MapleCharacter.loadCharFromDB(cid, new MapleClient(null, null, new tools.MockIOSession()), true);
    }

    public void cancelBuffStats(MapleBuffStat... stat) {
        List<MapleBuffStat> buffStatList = Arrays.asList(stat);
        unRegisterBuffStats(buffStatList);
        cancelPlayerBuffs(buffStatList);
    }

    public void cancelEffectFromBuffStat(MapleBuffStat stat) {
        if (effects.get(stat) != null) {
            cancelEffect(effects.get(stat).effect, false, -1);
        }
    }

    private void cancelPlayerBuffs(List<MapleBuffStat> buffstats) {
        boolean write = client.getChannelServer().getPlayerStorage().getCharacterById(getId()) != null;
        if (buffstats.contains(MapleBuffStat.HOMING_BEACON)) {
            if (write) {
                client.sendPacket(MaplePacketCreator.cancelHoming());
            }
        } else {
            if (write) {
                stats.recalcLocalStats();
            }
            client.sendPacket(MaplePacketCreator.cancelBuff(buffstats));
            map.broadcastMessage(this, MaplePacketCreator.cancelForeignBuff(getId(), buffstats), false);
        }
    }

    public void dispel() {
        if (!isHidden()) {
            final LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<>(effects.values());
            for (MapleBuffStatValueHolder mbsvh : allBuffs) {
                if (mbsvh.effect.isSkill() && mbsvh.schedule != null && !mbsvh.effect.isMorph() && !mbsvh.effect.isEnergyCharge()) {
                    cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                }
            }
        }
    }
    private String excluded = "";

    // Make sure to save and load this from the database like how others in handled. (In string of course)
    // store item ids as string with a splitter, eg : 1000000,1010000,1020000 and so on.
    public List<Integer> getPetItemIgnore() {
        List<Integer> ret = new ArrayList<>(10);
        if (!getExcluded().equals("")) {
            final String[] iig = getExcluded().split(",");
            for (final String x : iig) {
                ret.add(Integer.parseInt(x));
            }
        }
        return ret;
    }

    public String getExcluded() {
        return excluded;
    }

    public void setExcluded(final String ex) {
        this.excluded = ex;
    }

    public void dispelSkill(int skillid) {
        final LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<>(effects.values());

        for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            if (skillid == 0) {
                if (mbsvh.effect.isSkill() && (mbsvh.effect.getSourceId() == 4331003 || mbsvh.effect.getSourceId() == 4331002 || mbsvh.effect.getSourceId() == 4341002 || mbsvh.effect.getSourceId() == 22131001 || mbsvh.effect.getSourceId() == 1321007 || mbsvh.effect.getSourceId() == 2121005 || mbsvh.effect.getSourceId() == 2221005 || mbsvh.effect.getSourceId() == 2311006 || mbsvh.effect.getSourceId() == 2321003 || mbsvh.effect.getSourceId() == 3111002 || mbsvh.effect.getSourceId() == 3111005 || mbsvh.effect.getSourceId() == 3211002 || mbsvh.effect.getSourceId() == 3211005 || mbsvh.effect.getSourceId() == 4111002)) {
                    cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                    break;
                }
            } else if (mbsvh.effect.isSkill() && mbsvh.effect.getSourceId() == skillid) {
                cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                break;
            }
        }
    }

    public void dispelBuff(int skillid) {
        final LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<>(effects.values());

        for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            if (mbsvh.effect.getSourceId() == skillid) {
                cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                break;
            }
        }
    }

    public void cancelAllBuffs_() {
        effects.clear();
    }

    public void cancelAllBuffs() {
        final LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<>(effects.values());

        for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            cancelEffect(mbsvh.effect, false, mbsvh.startTime);
        }
    }

    public void cancelMorphs() {
        final LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<>(effects.values());

        for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            switch (mbsvh.effect.getSourceId()) {
                case 5111005:
                case 5121003:
                case 15111002:
                case 13111005:
                    return; // Since we can't have more than 1, save up on loops
                default:
                    if (mbsvh.effect.isMorph()) {
                        cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                    }
            }
        }
    }

    public int getMorphState() {
        LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<>(effects.values());
        for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            if (mbsvh.effect.isMorph()) {
                return mbsvh.effect.getSourceId();
            }
        }
        return -1;
    }

    public void silentGiveBuffs(List<PlayerBuffValueHolder> buffs) {
        if (buffs == null) {
            return;
        }
        for (PlayerBuffValueHolder mbsvh : buffs) {
            mbsvh.effect.silentApplyBuff(this, mbsvh.startTime, mbsvh.localDuration, mbsvh.statup, mbsvh.cid);
        }
    }

    public List<PlayerBuffValueHolder> getAllBuffs() {
        final List<PlayerBuffValueHolder> ret = new ArrayList<>();
        final Map<Pair<Integer, Byte>, Integer> alreadyDone = new HashMap<>();
        final LinkedList<Entry<MapleBuffStat, MapleBuffStatValueHolder>> allBuffs = new LinkedList<>(effects.entrySet());
        for (Entry<MapleBuffStat, MapleBuffStatValueHolder> mbsvh : allBuffs) {
            final Pair<Integer, Byte> key = new Pair<>(mbsvh.getValue().effect.getSourceId(), mbsvh.getValue().effect.getLevel());
            if (alreadyDone.containsKey(key)) {
                ret.get(alreadyDone.get(key)).statup.add((new Pair<>(mbsvh.getKey(), mbsvh.getValue().value)));
            } else {
                alreadyDone.put(key, ret.size());
                ArrayList<Pair<MapleBuffStat, Integer>> list = new ArrayList();
                list.add((new Pair<>(mbsvh.getKey(), mbsvh.getValue().value)));
                ret.add(new PlayerBuffValueHolder(mbsvh.getValue().startTime, mbsvh.getValue().effect, list, mbsvh.getValue().localDuration, mbsvh.getValue().cid));
            }
        }
        return ret;
    }

    public void cancelMagicDoor() {
        final LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<>(effects.values());

        for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            if (mbsvh.effect.isMagicDoor()) {
                cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                break;
            }
        }
    }

    public int getSkillLevel(int skillid) {
        return getSkillLevel(SkillFactory.getSkill(skillid));
    }

    public final void handleEnergyCharge(final int skillid, final int targets) {
        final ISkill echskill = SkillFactory.getSkill(skillid);
        final byte skilllevel = getSkillLevel(echskill);
        if (skilllevel > 0) {
            final MapleStatEffect echeff = echskill.getEffect(skilllevel);
            if (targets > 0) {
                if (getBuffedValue(MapleBuffStat.ENERGY_CHARGE) == null) {
                    echeff.applyEnergyBuff(this, true); // Infinity time
                } else {
                    Integer energyLevel = getBuffedValue(MapleBuffStat.ENERGY_CHARGE);
                    //TODO: bar going down
                    if (energyLevel < 10000) {
                        energyLevel += (echeff.getX() * targets);

                        client.sendPacket(MaplePacketCreator.showOwnBuffEffect(skillid, 2));
                        map.broadcastMessage(this, MaplePacketCreator.showBuffeffect(id, skillid, 2), false);

                        if (energyLevel >= 10000) {
                            energyLevel = 10000;
                        }
                        client.sendPacket(MaplePacketCreator.giveEnergyChargeTest(energyLevel, echeff.getDuration() / 1000));
                        setBuffedValue(MapleBuffStat.ENERGY_CHARGE, energyLevel);
                    } else if (energyLevel == 10000) {
                        echeff.applyEnergyBuff(this, false); // One with time
                        setBuffedValue(MapleBuffStat.ENERGY_CHARGE, 10001);
                    }
                }
            }
        }
    }

    public final void handleBattleshipHP(int damage) {
        if (isActiveBuffedValue(5221006)) {
            battleshipHP -= damage;
            if (battleshipHP <= 0) {
                battleshipHP = 0;
                final MapleStatEffect effect = getStatForBuff(MapleBuffStat.MONSTER_RIDING);
                client.sendPacket(MaplePacketCreator.skillCooldown(5221006, effect.getCooldown()));
                addCooldown(5221006, System.currentTimeMillis(), effect.getCooldown() * 1000);
                dispelSkill(5221006);
            }
        }
    }

    public final void handleOrbgain() {
        int orbcount = getBuffedValue(MapleBuffStat.COMBO);
        ISkill theCombol;
        ISkill advcombo;

        switch (getJob()) {
            case 1110:
            case 1111:
            case 1112:
                theCombol = SkillFactory.getSkill(11111001);
                advcombo = SkillFactory.getSkill(11110005);
                break;
            default:
                theCombol = SkillFactory.getSkill(1111002);
                advcombo = SkillFactory.getSkill(1120003);
                break;
        }

        MapleStatEffect ceffect;
        int advComboSkillLevel = getSkillLevel(advcombo);
        if (advComboSkillLevel > 0) {
            ceffect = advcombo.getEffect(advComboSkillLevel);
        } else if (getSkillLevel(theCombol) > 0) {
            ceffect = theCombol.getEffect(getSkillLevel(theCombol));
        } else {
            return;
        }

        if (orbcount < ceffect.getX() + 1) {
            int neworbcount = orbcount + 1;
            if (advComboSkillLevel > 0 && ceffect.makeChanceResult()) {
                if (neworbcount < ceffect.getX() + 1) {
                    neworbcount++;
                }
            }
            List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<>(MapleBuffStat.COMBO, neworbcount));
            setBuffedValue(MapleBuffStat.COMBO, neworbcount);
            int duration = ceffect.getDuration();
            duration += (int) ((getBuffedStarttime(MapleBuffStat.COMBO) - System.currentTimeMillis()));

            client.sendPacket(MaplePacketCreator.giveBuff(theCombol.getId(), duration, stat, ceffect));
            map.broadcastMessage(this, MaplePacketCreator.giveForeignBuff(getId(), stat, ceffect), false);
        }
    }

    public void handleOrbconsume() {
        ISkill theCombol;

        switch (getJob()) {
            case 1110:
            case 1111:
            case 1112:
                theCombol = SkillFactory.getSkill(11111001);
                break;
            default:
                theCombol = SkillFactory.getSkill(1111002);
                break;
        }
        if (getSkillLevel(theCombol) <= 0) {
            return;
        }
        MapleStatEffect ceffect = getStatForBuff(MapleBuffStat.COMBO);
        if (ceffect == null) {
            return;
        }
        List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<>(MapleBuffStat.COMBO, 1));
        setBuffedValue(MapleBuffStat.COMBO, 1);
        int duration = ceffect.getDuration();
        duration += (int) ((getBuffedStarttime(MapleBuffStat.COMBO) - System.currentTimeMillis()));

        client.sendPacket(MaplePacketCreator.giveBuff(theCombol.getId(), duration, stat, ceffect));
        map.broadcastMessage(this, MaplePacketCreator.giveForeignBuff(getId(), stat, ceffect), false);
    }

    public void silentEnforceMaxHpMp() {
        stats.setMp(stats.getMp());
        stats.setHp(stats.getHp(), true);
    }

    public void enforceMaxHpMp() {
        List<Pair<MapleStat, Integer>> statups = new ArrayList<>(2);
        if (stats.getMp() > stats.getCurrentMaxMp()) {
            stats.setMp(stats.getMp());
            boolean add = statups.add(new Pair<>(MapleStat.MP, Integer.valueOf(stats.getMp())));
        }
        if (stats.getHp() > stats.getCurrentMaxHp()) {
            stats.setHp(stats.getHp());
            statups.add(new Pair<>(MapleStat.HP, (int) stats.getHp()));
        }
        if (statups.size() > 0) {
            client.sendPacket(MaplePacketCreator.updatePlayerStats(statups, getJob()));
        }
    }

    public MapleMap getMap() {
        return map;
    }

    public MonsterBook getMonsterBook() {
        return monsterbook;
    }

    public void setMap(MapleMap newmap) {
        this.map = newmap;
    }

    public void setMap(int PmapId) {
        this.mapid = PmapId;
    }

    public int getMapId() {
        if (map != null) {
            return map.getId();
        }
        return mapid;
    }

    public byte getInitialSpawnpoint() {
        return initialSpawnPoint;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public final String getBlessOfFairyOrigin() {
        return this.BlessOfFairy_Origin;
    }

    public final short getLevel() {
        return level;
    }

    public final short getFame() {
        return fame;
    }

    public final int getDojo() {
        return dojo;
    }

    public final int getDojoRecord() {
        return dojoRecord;
    }

    public final int getFallCounter() {
        return fallcounter;
    }

    public final MapleClient getClient() {
        return client;
    }

    public final void setClient(final MapleClient client) {
        this.client = client;
    }

    public int getExp() {
        return exp;
    }

    public short getRemainingAp() {
        return remainingAp;
    }

    public int getRemainingSp() {
        return remainingSp[GameConstants.getSkillBook(job)]; //default
    }

    public int getRemainingSp(final int skillbook) {
        return remainingSp[skillbook];
    }

    public int[] getRemainingSps() {
        return remainingSp;
    }

    public int getRemainingSpSize() {
        int ret = 0;
        for (int i = 0; i < remainingSp.length; i++) {
            if (remainingSp[i] > 0) {
                ret++;
            }
        }
        return ret;
    }

    public short getHpMpApUsed() {
        return hpmpApUsed;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHpMpApUsed(short hpApUsed) {
        this.hpmpApUsed = hpApUsed;
    }

    public byte getSkinColor() {
        return skinColor;
    }

    public void setSkinColor(byte skinColor) {
        this.skinColor = skinColor;
    }

    public short getJob() {
        return job;
    }

    public byte getGender() {
        return gender;
    }

    public int getHair() {
        return hair;
    }

    public int getFace() {
        return face;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setExp(int exp) {
        this.exp = exp;
    }

    public void setHair(int hair) {
        this.hair = hair;
    }

    public void setFace(int face) {
        this.face = face;
    }

    public void setFame(short fame) {
        this.fame = fame;
    }

    public void setDojo(final int dojo) {
        this.dojo = dojo;
    }

    public void setDojoRecord(final boolean reset) {
        if (reset) {
            dojo = 0;
            dojoRecord = 0;
        } else {
            dojoRecord++;
        }
    }

    public void setFallCounter(int fallcounter) {
        this.fallcounter = fallcounter;
    }

    public Point getOldPosition() {
        return old;
    }

    public void setOldPosition(Point x) {
        this.old = x;
    }

    public void setRemainingAp(short remainingAp) {
        this.remainingAp = remainingAp;
    }

    public void setRemainingSp(int remainingSp) {
        this.remainingSp[GameConstants.getSkillBook(job)] = remainingSp; //default
    }

    public void setRemainingSp(int remainingSp, final int skillbook) {
        this.remainingSp[skillbook] = remainingSp;
    }

    public void setGender(byte gender) {
        this.gender = gender;
    }

    public void setInvincible(boolean invinc) {
        invincible = invinc;
    }

    public boolean isShowDebugInfo() {
        return this.isShowDebugInfo;
    }

    public void setShowDebugInfo(boolean value) {
        this.isShowDebugInfo = value;
    }

    public boolean isInvincible() {
        return invincible;
    }

    public CheatTracker getCheatTracker() {
        return anticheat;
    }

    public BuddyList getBuddylist() {
        return buddylist;
    }

    public void addFame(int famechange) {
        this.fame += famechange;
        /*if (this.fame >= 50) {
         finishAchievement(7);
         }*/
    }

    public void changeMap(final int mapid) {
        final MapleMap target = client.getChannelServer().getMapFactory().getMap(mapid);
        changeMap(target, target.getPortal(0));
    }

    public void changeMapBanish(final int mapid, final String portal, final String msg) {
        dropMessage(5, msg);
        final MapleMap target = client.getChannelServer().getMapFactory().getMap(mapid);
        changeMap(target, target.getPortal(portal));
    }

    public void changeMap(final MapleMap to, final Point pos) {
        changeMapInternal(to, pos, MaplePacketCreator.getWarpToMap(to, 0x81, this), null);
    }

    public void changeMap(final MapleMap to, final MaplePortal pto) {
        changeMapInternal(to, pto.getPosition(), MaplePacketCreator.getWarpToMap(to, pto.getId(), this), null);
    }

    public void changeMapPortal(final MapleMap to, final MaplePortal pto) {
        changeMapInternal(to, pto.getPosition(), MaplePacketCreator.getWarpToMap(to, pto.getId(), this), pto);
    }

    private void changeMapInternal(final MapleMap to, final Point pos, MaplePacket warpPacket, final MaplePortal pto) {
        if (to == null) {
            return;
        }
        final int nowmapid = map.getId();
        if (eventInstance != null) {
            eventInstance.changedMap(this, to.getId());
        }
        final boolean pyramid = pyramidSubway != null;
        if (map.getId() == nowmapid) {
            client.sendPacket(warpPacket);

            map.removePlayer(this);
            if (!isClone() && client.getChannelServer().getPlayerStorage().getCharacterById(getId()) != null) {
                map = to;
                setPosition(pos);
                to.addPlayer(this);
                stats.relocHeal();
            }
        }
        if (pyramid && pyramidSubway != null) { //checks if they had pyramid before AND after changing
            pyramidSubway.onChangeMap(this, to.getId());
        }
    }

    public void leaveMap() {
        //controlled.clear();
        visibleMapObjectsLock.writeLock().lock();
        try {
            visibleMapObjects.clear();
        } finally {
            visibleMapObjectsLock.writeLock().unlock();
        }
        if (chair != 0) {
            cancelFishingTask();
            chair = 0;
        }
        cancelMapTimeLimitTask();
    }

    public void changeJob(int newJob) {
        try {

            this.job = (short) newJob;
            if (newJob != 0 && newJob != 1000 && newJob != 2000 && newJob != 2001 && newJob != 3000) {
                remainingSp[GameConstants.getSkillBook(newJob)]++;
                if (newJob % 10 >= 2) {
                    remainingSp[GameConstants.getSkillBook(newJob)] += 2;
                }
            }
            if (newJob > 0 && !isGM()) {
                resetStatsByJob(true);
                if (newJob == 2200) {
                    MapleQuest.getInstance(22100).forceStart(this, 0, null);
                    MapleQuest.getInstance(22100).forceComplete(this, 0);
                    expandInventory((byte) 1, 4);
                    expandInventory((byte) 2, 4);
                    expandInventory((byte) 3, 4);
                    expandInventory((byte) 4, 4);
                    client.sendPacket(MaplePacketCreator.getEvanTutorial("UI/tutorial/evan/14/0"));
                    dropMessage(5, "The baby Dragon hatched and appears to have something to tell you. Click the baby Dragon to start a conversation.");
                }
            }
            client.sendPacket(MaplePacketCreator.updateSp(this, false));
            updateSingleStat(MapleStat.JOB, newJob);

            int maxhp = stats.getMaxHp(), maxmp = stats.getMaxMp();

            switch (job) {
                case 100: // Warrior
                case 1100: // Soul Master
                case 2100: // Aran
                case 3200:
                    maxhp += Randomizer.rand(200, 250);
                    break;
                case 200: // Magician
                case 2200: //evan
                case 2210: //evan
                    maxmp += Randomizer.rand(100, 150);
                    break;
                case 300: // Bowman
                case 400: // Thief
                case 500: // Pirate
                case 3300:
                case 3500:
                    maxhp += Randomizer.rand(100, 150);
                    maxmp += Randomizer.rand(25, 50);
                    break;
                case 110: // Fighter
                    maxhp += Randomizer.rand(300, 350);
                    break;
                case 120: // Page
                case 130: // Spearman
                case 510: // 打手
                case 512: // 拳霸
                case 1110: // Soul Master
                case 2110: // Aran
                case 3210:
                    maxhp += Randomizer.rand(300, 350);
                    break;
                case 210: // FP
                case 220: // IL
                case 230: // Cleric
                    maxmp += Randomizer.rand(400, 450);
                    break;
                case 310: // Bowman
                case 312: // Bowman
                case 320: // Crossbowman
                case 322: // Bowman
                case 410: // Assasin
                case 412: // Assasin
                case 420: // Bandit
                case 422: // Assasin
                case 430: // Semi Dualer
                case 520: // 槍手
                case 522: // 槍神
                case 1310: // Wind Breaker
                case 1410: // Night Walker
                case 3310:
                case 3510:
                    maxhp += Randomizer.rand(300, 350);
                    maxhp += Randomizer.rand(150, 200);
                    break;
                case 900: // GM
                case 800: // Manager
                    maxhp += 30000;
                    maxhp += 30000;
                    break;
            }
            if (maxhp >= 30000) {
                maxhp = 30000;
            }
            if (maxmp >= 30000) {
                maxmp = 30000;
            }
            stats.setMaxHp((short) maxhp);
            stats.setMaxMp((short) maxmp);
            stats.setHp((short) maxhp);
            stats.setMp((short) maxmp);
            List<Pair<MapleStat, Integer>> statup = new ArrayList<>(4);
            statup.add(new Pair<>(MapleStat.MAXHP, maxhp));
            statup.add(new Pair<>(MapleStat.MAXMP, maxmp));
            statup.add(new Pair<>(MapleStat.HP, maxhp));
            statup.add(new Pair<>(MapleStat.MP, maxmp));
            stats.recalcLocalStats();
            client.sendPacket(MaplePacketCreator.updatePlayerStats(statup, getJob()));
            map.broadcastMessage(this, MaplePacketCreator.showForeignEffect(getId(), 8), false);
            silentPartyUpdate();
            guildUpdate();
            familyUpdate();
            baseSkills();
            if (newJob >= 2200 && newJob <= 2218) { //make new
                if (getBuffedValue(MapleBuffStat.MONSTER_RIDING) != null) {
                    cancelBuffStats(MapleBuffStat.MONSTER_RIDING);
                }
            }
        } catch (Exception e) {
            FilePrinter.printError("MapleCharacter.txt", e);
        }
    }

    public void baseSkills() {
        if (GameConstants.getJobNumber(job) >= 3) { //third job.
            List<Integer> baseSkills = SkillFactory.getSkillsByJob(job);
            if (baseSkills != null) {
                for (int i : baseSkills) {
                    final ISkill skil = SkillFactory.getSkill(i);
                    if (skil != null && !skil.isInvisible() && skil.isFourthJob() && getSkillLevel(skil) <= 0 && getMasterLevel(skil) <= 0 && skil.getMasterLevel() > 0) {
                        changeSkillLevel(skil, (byte) 0, (byte) skil.getMasterLevel()); //usually 10 master
                    }
                }
            }
        }
    }

    public void gainAp(short ap) {
        this.remainingAp += ap;
        updateSingleStat(MapleStat.AVAILABLEAP, this.remainingAp);
    }

    public void gainSP(int sp) {
        this.remainingSp[GameConstants.getSkillBook(job)] += sp; //default
        client.sendPacket(MaplePacketCreator.updateSp(this, false));
        client.sendPacket(UIPacket.getSPMsg((byte) sp, (short) job));
    }

    public void gainSP(int sp, final int skillbook) {
        this.remainingSp[skillbook] += sp; //default
        client.sendPacket(MaplePacketCreator.updateSp(this, false));
        client.sendPacket(UIPacket.getSPMsg((byte) sp, (short) job));
    }

    public void resetAPSP() {
        for (int i = 0; i < remainingSp.length; i++) {
            this.remainingSp[i] = 0;
        }
        client.sendPacket(MaplePacketCreator.updateSp(this, false));
        gainAp((short) -this.remainingAp);
    }

    public void changeSkillLevel(final ISkill skill, byte newLevel, byte newMasterlevel) { //1 month
        if (skill == null) {
            return;
        }
        changeSkillLevel(skill, newLevel, newMasterlevel, skill.isTimeLimited() ? (System.currentTimeMillis() + (long) (30L * 24L * 60L * 60L * 1000L)) : -1);
    }

    public void changeSkillLevel(final ISkill skill, byte newLevel, byte newMasterlevel, long expiration) {
        if (skill == null || (!GameConstants.isApplicableSkill(skill.getId()) && !GameConstants.isApplicableSkill_(skill.getId()))) {
            return;
        }
        client.sendPacket(MaplePacketCreator.updateSkill(skill.getId(), newLevel, newMasterlevel, expiration));
        if (newLevel == 0 && newMasterlevel == 0) {
            if (skills.containsKey(skill)) {
                skills.remove(skill);
            } else {
                return; //nothing happen
            }
        } else {
            skills.put(skill, new SkillEntry(newLevel, newMasterlevel, expiration));
        }
        if (GameConstants.isRecoveryIncSkill(skill.getId())) {
            stats.relocHeal();
        } else if (GameConstants.isElementAmpSkill(skill.getId())) {
            stats.recalcLocalStats();
        }

    }

    public void changeSkillLevel_Skip(final ISkill skill, byte newLevel, byte newMasterlevel) {
        if (skill == null) {
            return;
        }
        client.sendPacket(MaplePacketCreator.updateSkill(skill.getId(), newLevel, newMasterlevel, -1L));
        if (newLevel == 0 && newMasterlevel == 0) {
            if (skills.containsKey(skill)) {
                skills.remove(skill);
            }
        } else {
            skills.put(skill, new SkillEntry(newLevel, newMasterlevel, -1L));
        }

    }

    public void playerDead() {
        final MapleStatEffect statss = getStatForBuff(MapleBuffStat.SOUL_STONE);
        if (statss != null) {
            dropMessage(5, "你已經通過靈魂之石復活了。");
            getStat().setHp(((getStat().getMaxHp() / 100) * statss.getX()));
            setStance(0);
            changeMap(getMap(), getMap().getPortal(0));
            return;
        }
        if (getEventInstance() != null) {
            getEventInstance().playerKilled(this);
        }
        dispelSkill(0);
        cancelEffectFromBuffStat(MapleBuffStat.MORPH);
        cancelEffectFromBuffStat(MapleBuffStat.MONSTER_RIDING);
        cancelEffectFromBuffStat(MapleBuffStat.SUMMON);
        cancelEffectFromBuffStat(MapleBuffStat.PUPPET);
        checkFollow();
        if (job != 0 && job != 1000 && job != 2000 && job != 2001 && job != 3000) {
            int charms = getItemQuantity(5130000, false);
            if (charms > 0) {
                MapleInventoryManipulator.removeById(client, MapleInventoryType.CASH, 5130000, 1, true, false);

                charms--;
                if (charms > 0xFF) {
                    charms = 0xFF;
                }
                client.sendPacket(MTSCSPacket.useCharm((byte) charms, (byte) 0));
            } else {
                float diepercentage;
                int expforlevel = GameConstants.getExpNeededForLevel(level);
                if (map.isTown() || FieldLimitType.RegularExpLoss.check(map.getFieldLimit())) {
                    diepercentage = 0.01f;
                } else {
                    float v8;
                    if (this.job / 100 == 3) {
                        v8 = 0.08f;
                    } else {
                        v8 = 0.2f;
                    }
                    diepercentage = (float) (v8 / this.stats.getLuk() + 0.05);
                }
                int v10 = (int) (exp - (long) ((double) expforlevel * diepercentage));
                if (v10 < 0) {
                    v10 = 0;
                }
                this.exp = v10;
            }
        }
        this.updateSingleStat(MapleStat.EXP, this.exp);
        if (!stats.checkEquipDurabilitys(this, -100)) { //i guess this is how it works ?
            dropMessage(5, "該裝備耐久度已經使用完畢，必須修理才可以繼續使用。");
        } //lol
        if (pyramidSubway != null) {
            stats.setHp((short) 50);
            pyramidSubway.fail(this);
        }
    }

    public void updatePartyMemberHP() {
        if (party != null) {
            final int channel = client.getChannel();
            for (MaplePartyCharacter partychar : party.getMembers()) {
                if (partychar.getMapid() == getMapId() && partychar.getChannel() == channel) {
                    final MapleCharacter other = client.getChannelServer().getPlayerStorage().getCharacterByName(partychar.getName());
                    if (other != null) {
                        other.getClient().sendPacket(MaplePacketCreator.updatePartyMemberHP(getId(), stats.getHp(), stats.getCurrentMaxHp()));
                    }
                }
            }
        }
    }

    public void receivePartyMemberHP() {
        if (party == null) {
            return;
        }
        int channel = client.getChannel();
        for (MaplePartyCharacter partychar : party.getMembers()) {
            if (partychar.getMapid() == getMapId() && partychar.getChannel() == channel) {
                MapleCharacter other = client.getChannelServer().getPlayerStorage().getCharacterByName(partychar.getName());
                if (other != null) {
                    client.sendPacket(MaplePacketCreator.updatePartyMemberHP(other.getId(), other.getStat().getHp(), other.getStat().getCurrentMaxHp()));
                }
            }
        }
    }

    public void healHP(int delta) {
        healHP(delta, false);
    }

    public void healHP(int delta, boolean Show) {
        addHP(delta);
        if (Show) {
            client.sendPacket(MaplePacketCreator.showOwnHpHealed(delta));
            getMap().broadcastMessage(this, MaplePacketCreator.showHpHealed(getId(), delta), false);
        }
    }

    public void healMP(int delta) {
        addMP(delta);
//        client.sendPacket(MaplePacketCreator.showOwnHpHealed(delta));
//        getMap().broadcastMessage(this, MaplePacketCreator.showHpHealed(getId(), delta), false);
    }

    /**
     * Convenience function which adds the supplied parameter to the current hp
     * then directly does a updateSingleStat.
     *
     * @see MapleCharacter#setHp(int)
     * @param delta
     */
    public void addHP(int delta) {
        if (stats.setHp(stats.getHp() + delta)) {
            updateSingleStat(MapleStat.HP, stats.getHp());
        }
    }

    /**
     * Convenience function which adds the supplied parameter to the current mp
     * then directly does a updateSingleStat.
     *
     * @see MapleCharacter#setMp(int)
     * @param delta
     */
    public void addMP(int delta) {
        if (stats.setMp(stats.getMp() + delta)) {
            updateSingleStat(MapleStat.MP, stats.getMp());
        }
    }

    public void addMPHP(int hpDiff, int mpDiff) {
        List<Pair<MapleStat, Integer>> statups = new ArrayList<>();

        if (stats.setHp(stats.getHp() + hpDiff)) {
            statups.add(new Pair<>(MapleStat.HP, Integer.valueOf(stats.getHp())));
        }
        if (stats.setMp(stats.getMp() + mpDiff)) {
            statups.add(new Pair<>(MapleStat.MP, Integer.valueOf(stats.getMp())));
        }
        if (statups.size() > 0) {
            client.sendPacket(MaplePacketCreator.updatePlayerStats(statups, getJob()));
        }
    }

    public void updateSingleStat(MapleStat stat, int newval) {
        updateSingleStat(stat, newval, false);
    }

    /**
     * Updates a single stat of this MapleCharacter for the client. This method
     * only creates and sends an update packet, it does not update the stat
     * stored in this MapleCharacter instance.
     *
     * @param stat
     * @param newval
     * @param itemReaction
     */
    public void updateSingleStat(MapleStat stat, int newval, boolean itemReaction) {
        if (stat == MapleStat.AVAILABLESP) {
            client.sendPacket(MaplePacketCreator.updateSp(this, itemReaction));
            return;
        }
        Pair<MapleStat, Integer> statpair = new Pair<>(stat, Integer.valueOf(newval));
        client.sendPacket(MaplePacketCreator.updatePlayerStats(Collections.singletonList(statpair), itemReaction, getJob()));
    }

    public void gainExp(final int total, final boolean show, final boolean inChat, final boolean white) {
        try {
            expirationTask(true, false);
            int prevexp = getExp();
            int needed = GameConstants.getExpNeededForLevel(level);
            if (total > 0) {
                stats.checkEquipLevels(this, total); //gms like
            }
            if (GameConstants.isKOC(job) && level >= 120) {
                setExp(0);
            }
            if (level >= 200) {
                if (exp + total > needed) {
                    setExp(needed);
                } else {
                    exp += total;
                }
            } else {
                boolean leveled = false;
                if (exp + total >= needed) {
                    exp += total;
                    while (exp >= needed) {
                        levelUp();
                        leveled = true;
                        needed = GameConstants.getExpNeededForLevel(level);
                    }
                } else {
                    exp += total;
                }
                if (total > 0) {
                    familyRep(prevexp, needed, leveled);
                }
            }
            if (total != 0) {
                if (exp < 0) { // After adding, and negative
                    if (total > 0) {
                        setExp(needed);
                    } else if (total < 0) {
                        setExp(0);
                    }
                }
                updateSingleStat(MapleStat.EXP, getExp());
                if (show) { // still show the expgain even if it's not there
                    client.sendPacket(MaplePacketCreator.GainEXPOthers(total, inChat, white));
                }
            }
        } catch (Exception e) {
            FilePrinter.printError("MapleCharacter.txt", e);
        }
    }

    public void familyRep(int prevexp, int needed, boolean leveled) {
        if (mfc != null) {
            int onepercent = needed / 100;
            int percentrep = (prevexp / onepercent + getExp() / onepercent);
            if (leveled) {
                percentrep = 100 - percentrep + (level / 2);
            }
            if (percentrep > 0) {
                int sensen = World.Family.setRep(mfc.getFamilyId(), mfc.getSeniorId(), percentrep, level, name);
                if (sensen > 0) {
                    World.Family.setRep(mfc.getFamilyId(), sensen, percentrep / 2, level, name); //and we stop here
                }
            }
        }
    }

    public void gainExpMonster(final int gain, final boolean show, final boolean white, final byte pty, int Class_Bonus_EXP, int Equipment_Bonus_EXP, int Premium_Bonus_EXP) {
        expirationTask(true, false);
        int total = gain + Class_Bonus_EXP + Equipment_Bonus_EXP + Premium_Bonus_EXP;
        int partyinc = 0;
        int prevexp = getExp();
        if (pty > 1) {
            partyinc = (int) (((float) (gain / 5.0)) * (pty + 1)); //modify (gain / 20.0), the party exp
            total += partyinc;
        }

        if (gain > 0 && total < gain) { //just in case
            total = Integer.MAX_VALUE;
        }
        if (total > 0) {
            stats.checkEquipLevels(this, total);
        }

        int needed = GameConstants.getExpNeededForLevel(level);
        if (GameConstants.isKOC(job) && level >= 120) {
            return;
        }
        if (level >= 200) {
            if (exp + total > needed) {
                setExp(needed);
            } else {
                exp += total;
            }
        } else {
            boolean leveled = false;
            if (exp + total >= needed) {
                exp += total;
                levelUp();
                leveled = true;
                needed = GameConstants.getExpNeededForLevel(level);
                if (exp > needed) {
                    setExp(needed);
                }
            } else {
                exp += total;
            }
            if (total > 0) {
                familyRep(prevexp, needed, leveled);
            }
        }
        if (gain != 0) {
            if (exp < 0) { // After adding, and negative
                if (gain > 0) {
                    setExp(GameConstants.getExpNeededForLevel(level));
                } else if (gain < 0) {
                    setExp(0);
                }
            }
            updateSingleStat(MapleStat.EXP, getExp());
            if (show) { // still show the expgain even if it's not there
                client.sendPacket(MaplePacketCreator.GainEXP_Monster(gain, white, partyinc, Class_Bonus_EXP, Equipment_Bonus_EXP, Premium_Bonus_EXP));
            }
        }
    }

    public void reloadC() {
        client.getPlayer().getClient().sendPacket(MaplePacketCreator.getCharInfo(client.getPlayer()));
        client.getPlayer().getMap().removePlayer(client.getPlayer());
        client.getPlayer().getMap().addPlayer(client.getPlayer());
    }

    public void forceReAddItem_NoUpdate(IItem item, MapleInventoryType type) {
        getInventory(type).removeSlot(item.getPosition());
        getInventory(type).addFromDB(item);
    }

    public void forceReAddItem(IItem item, MapleInventoryType type) { //used for stuff like durability, item exp/level, probably owner?
        forceReAddItem_NoUpdate(item, type);
        if (type != MapleInventoryType.UNDEFINED) {
            client.sendPacket(MaplePacketCreator.modifyInventory(false, new ModifyInventory(ModifyInventory.Types.UPDATE, item)));
            //client.sendPacket(MaplePacketCreator.updateSpecialItemUse(item, type == MapleInventoryType.EQUIPPED ? (byte) 1 : type.getType()));
        }
    }

    public void forceReAddItem_Flag(IItem item, MapleInventoryType type) { //used for flags
        forceReAddItem_NoUpdate(item, type);
        if (type != MapleInventoryType.UNDEFINED) {
            client.sendPacket(MaplePacketCreator.modifyInventory(false, new ModifyInventory(ModifyInventory.Types.ADD, item)));

            //client.sendPacket(MaplePacketCreator.updateSpecialItemUse_(item, type == MapleInventoryType.EQUIPPED ? (byte) 1 : type.getType()));
        }
    }

    public void silentPartyUpdate() {
        if (party != null) {
            World.Party.updateParty(party.getId(), PartyOperation.SILENT_UPDATE, new MaplePartyCharacter(this));
        }
    }

    public boolean isGM() {
        return gmLevel > 0;
    }

    public boolean isAdmin() {
        return gmLevel >= 2;
    }

    public int getGMLevel() {
        return gmLevel;
    }

    public boolean isPlayer() {
        return gmLevel == 0;
    }

    public boolean hasGmLevel(int level) {
        return gmLevel >= level;
    }

    public final MapleInventory getInventory(MapleInventoryType type) {
        return inventory[type.ordinal()];
    }

    public final MapleInventory[] getInventorys() {
        return inventory;
    }

    public final void expirationTask() {
        expirationTask(false);
    }

    public final void expirationTask(boolean pending) {
        expirationTask(false, pending);
    }

    public final void expirationTask(boolean packet, boolean pending) {
        if (pending) {
            if (pendingExpiration != null) {
                for (Integer z : pendingExpiration) {
                    client.sendPacket(MTSCSPacket.itemExpired(z));
                }
            }
            pendingExpiration = null;
            if (pendingSkills != null) {
                for (Integer z : pendingSkills) {
                    client.sendPacket(MaplePacketCreator.updateSkill(z, 0, 0, -1));
                    client.sendPacket(MaplePacketCreator.getErrorNotice("[" + SkillFactory.getSkillName(z) + "] 技能已經過期，系統自動從技能欄位移除。"));
                }
            } //not real msg
            pendingSkills = null;
            return;
        }
        long expiration;
        final List<Integer> ret = new ArrayList<>();
        final long currenttime = System.currentTimeMillis();
        final List<Pair<MapleInventoryType, IItem>> toberemove = new ArrayList<>(); // This is here to prevent deadlock.
        final List<IItem> tobeunlock = new ArrayList<>(); // This is here to prevent deadlock.

        for (final MapleInventoryType inv : MapleInventoryType.values()) {
            for (final IItem item : getInventory(inv)) {
                expiration = item.getExpiration();

                if (expiration != -1 && !GameConstants.寵物(item.getItemId()) && currenttime > expiration) {
                    if (ItemFlag.LOCK.check(item.getFlag())) {
                        tobeunlock.add(item);
                    } else if (currenttime > expiration) {
                        toberemove.add(new Pair<>(inv, item));
                    }
                } else if (item.getItemId() == 5000054 && item.getPet() != null && item.getPet().getSecondsLeft() <= 0) {
                    toberemove.add(new Pair<>(inv, item));
                }
            }
        }
        IItem item;
        for (final Pair<MapleInventoryType, IItem> itemz : toberemove) {
            item = itemz.getRight();
            ret.add(item.getItemId());
            if (packet) {
                getInventory(itemz.getLeft()).removeItem(item.getPosition(), item.getQuantity(), false, this);
            } else {
                getInventory(itemz.getLeft()).removeItem(item.getPosition(), item.getQuantity(), false);
            }
        }
        for (final IItem itemz : tobeunlock) {
            itemz.setExpiration(-1);
            itemz.setFlag((byte) (itemz.getFlag() - ItemFlag.LOCK.getValue()));
        }
        this.pendingExpiration = ret;

        final List<Integer> skilz = new ArrayList<>();
        final List<ISkill> toberem = new ArrayList<>();
        for (Entry<ISkill, SkillEntry> skil : skills.entrySet()) {
            if (skil.getValue().expiration != -1 && currenttime > skil.getValue().expiration) {
                toberem.add(skil.getKey());
            }
        }
        for (ISkill skil : toberem) {
            skilz.add(skil.getId());
            this.skills.remove(skil);
        }
        this.pendingSkills = skilz;
    }

    public MapleShop getShop() {
        return shop;
    }

    public void setShop(MapleShop shop) {
        this.shop = shop;
    }

    public int getMeso() {
        return meso;
    }

    public final int[] getSavedLocations() {
        return savedLocations;
    }

    public int getSavedLocation(SavedLocationType type) {
        return savedLocations[type.getValue()];
    }

    public void saveLocation(SavedLocationType type) {
        savedLocations[type.getValue()] = getMapId();
    }

    public void saveLocation(SavedLocationType type, int mapz) {
        savedLocations[type.getValue()] = mapz;
    }

    public void clearSavedLocation(SavedLocationType type) {
        savedLocations[type.getValue()] = -1;
    }

    public void gainMeso(int gain, boolean show) {
        gainMeso(gain, show, false, false);
    }

    public void gainMeso(int gain, boolean show, boolean enableActions) {
        gainMeso(gain, show, enableActions, false);
    }

    public void gainMeso(int gain, boolean show, boolean enableActions, boolean inChat) {
        if (meso + gain < 0) {
            client.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        meso += gain;
        updateSingleStat(MapleStat.MESO, meso, enableActions);
        if (show) {
            client.sendPacket(MaplePacketCreator.showMesoGain(gain, inChat));
        }
    }

    public void controlMonster(MapleMonster monster, boolean aggro) {
        if (clone) {
            return;
        }
        monster.setController(this);
        controlled.add(monster);
        client.sendPacket(MobPacket.controlMonster(monster, false, aggro));
    }

    public void stopControllingMonster(MapleMonster monster) {
        if (clone) {
            return;
        }
        if (monster != null && controlled.contains(monster)) {
            controlled.remove(monster);
        }
    }

    public void getSwitchHiredMerchant(boolean xx) {
        switchHiredMerchant = xx;
    }

    public boolean getSwitchHiredMerchant() {
        return switchHiredMerchant;
    }

    public void get玩家私聊1(boolean xx) {
        玩家私聊1 = xx;
    }

    public boolean get玩家私聊1() {
        return 玩家私聊1;
    }

    public void get玩家私聊2(boolean xx) {
        玩家私聊2 = xx;
    }

    public boolean get玩家私聊2() {
        return 玩家私聊2;
    }

    public void get玩家私聊3(boolean xx) {
        玩家私聊3 = xx;
    }

    public boolean get玩家私聊3() {
        return 玩家私聊3;
    }

    public void getGMinfo(boolean xx) {
        GMinfo = xx;
    }

    public boolean getGMinfo() {
        return GMinfo;
    }

    public void getCTitle(boolean xx) {
        聊天稱號 = xx;
    }

    public boolean getCTitle() {
        return 聊天稱號;
    }

    public void getGMChat(boolean xx) {
        GM聊天 = xx;
    }

    public boolean getGMChat() {
        return GM聊天;
    }

    public void setChatTitle(String text) {
        this.chattitle = text;
    }

    public String getChatTitle() {
        return chattitle;
    }

    public void checkMonsterAggro(MapleMonster monster) {
        if (clone || monster == null) {
            return;
        }
        if (monster.getController() == this) {
            monster.setControllerHasAggro(true);
        } else {
            monster.switchController(this, true);
        }
    }

    public Set<MapleMonster> getControlled() {
        return controlled;
    }

    public int getControlledSize() {
        return controlled.size();
    }

    public int getAccountID() {
        return accountid;
    }

    public void mobKilled(final int id, final int skillID) {
        for (MapleQuestStatus q : quests.values()) {
            if (q.getStatus() != 1 || !q.hasMobKills()) {
                continue;
            }
            if (q.mobKilled(id, skillID)) {
                client.sendPacket(MaplePacketCreator.updateQuestMobKills(q));
                if (q.getQuest().canComplete(this, null)) {
                    client.sendPacket(MaplePacketCreator.getShowQuestCompletion(q.getQuest().getId()));
                }
            }
        }
    }

    public final List<MapleQuestStatus> getStartedQuests() {
        List<MapleQuestStatus> ret = new LinkedList<>();
        for (MapleQuestStatus q : quests.values()) {
            if (q.getStatus() == 1 && !(q.isCustom())) {
                ret.add(q);
            }
        }
        return ret;
    }

    public final List<MapleQuestStatus> getCompletedQuests() {
        List<MapleQuestStatus> ret = new LinkedList<>();
        for (MapleQuestStatus q : quests.values()) {
            if (q.getStatus() == 2 && !(q.isCustom())) {
                ret.add(q);
            }
        }
        return ret;
    }

    public Map<ISkill, SkillEntry> getSkills() {
        return Collections.unmodifiableMap(skills);
    }

    public byte getSkillLevel(final ISkill skill) {
        final SkillEntry ret = skills.get(skill);
        if (ret == null || ret.skillevel <= 0) {
            return 0;
        }
        return (byte) Math.min(skill.getMaxLevel(), ret.skillevel + (skill.isBeginnerSkill() ? 0 : stats.incAllskill));
    }

    public byte getMasterLevel(final int skill) {
        return getMasterLevel(SkillFactory.getSkill(skill));
    }

    public byte getMasterLevel(final ISkill skill) {
        final SkillEntry ret = skills.get(skill);
        if (ret == null) {
            return 0;
        }
        return ret.masterlevel;
    }

    public void DoLevelMap() {
        boolean warp = false;
        int Return_Map = 0;
        switch (getMapId()) {
            case 910060000:
            case 910060001:
            case 910060002:
            case 910060003:
            case 910060004:
                warp = true;
                Return_Map = 100010000;
                break;
            case 910220000:
            case 910220001:
            case 910220002:
            case 910220003:
            case 910220004:
                warp = true;
                Return_Map = 101040000;
                break;
            case 910310000:
            case 910310001:
            case 910310002:
            case 910310003:
            case 910310004:
                warp = true;
                Return_Map = 103010000;
                break;
            case 912030000:
            case 912030001:
            case 912030002:
            case 912030003:
            case 912030004:
                warp = true;
                Return_Map = 120010000;
                break;
            case 910120000:
            case 910120001:
            case 910120002:
            case 910120003:
            case 910120004:
                warp = true;
                Return_Map = 100040000;
                break;
        }
        if (warp) {
            MapleMap warpMap = client.getChannelServer().getMapFactory().getMap(Return_Map);
            if (warpMap != null) {
                changeMap(warpMap, warpMap.getPortal(0));
                dropMessage("由於你的等級超過20，已經不符合新手需求，將把您傳出訓練場。");
            }
        }
    }

    public void levelUp() {
        if (GameConstants.isKOC(job)) {
            if (level <= 70) {
                remainingAp += 6;
            } else {
                remainingAp += 5;
            }
        } else {
            remainingAp += 5;
        }
        int maxhp = stats.getMaxHp();
        int maxmp = stats.getMaxMp();
        if (level >= 19) {
            DoLevelMap();
        }
        if (job == 0 || job == 1000 || job == 2000 || job == 2001 || job == 3000) { // Beginner
            maxhp += Randomizer.rand(12, 16);
            maxmp += Randomizer.rand(10, 12);
        } else if (job >= 100 && job <= 132) { // Warrior
            final ISkill improvingMaxHP = SkillFactory.getSkill(1000001);
            final int slevel = getSkillLevel(improvingMaxHP);
            if (slevel > 0) {
                maxhp += improvingMaxHP.getEffect(slevel).getX();
            }
            maxhp += Randomizer.rand(24, 28);
            maxmp += Randomizer.rand(4, 6);
        } else if (job >= 200 && job <= 232) { // Magician
            final ISkill improvingMaxMP = SkillFactory.getSkill(2000001);
            final int slevel = getSkillLevel(improvingMaxMP);
            if (slevel > 0) {
                maxmp += improvingMaxMP.getEffect(slevel).getX() * 2;
            }
            maxhp += Randomizer.rand(10, 14);
            maxmp += Randomizer.rand(22, 24);
        } else if (job >= 3200 && job <= 3212) { //battle mages get their own little neat thing
            maxhp += Randomizer.rand(20, 24);
            maxmp += Randomizer.rand(42, 44);
        } else if ((job >= 300 && job <= 322) || (job >= 400 && job <= 434) || (job >= 1300 && job <= 1311) || (job >= 1400 && job <= 1411) || (job >= 3300 && job <= 3312)) { // Bowman, Thief, Wind Breaker and Night Walker
            maxhp += Randomizer.rand(20, 24);
            maxmp += Randomizer.rand(14, 16);
        } else if ((job >= 500 && job <= 522) || (job >= 3500 && job <= 3512)) { // Pirate
            final ISkill improvingMaxHP = SkillFactory.getSkill(5100000);
            final int slevel = getSkillLevel(improvingMaxHP);
            if (slevel > 0) {
                maxhp += improvingMaxHP.getEffect(slevel).getX();
            }
            maxhp += Randomizer.rand(22, 26);
            maxmp += Randomizer.rand(18, 22);
        } else if (job >= 1100 && job <= 1111) { // Soul Master
            final ISkill improvingMaxHP = SkillFactory.getSkill(11000000);
            final int slevel = getSkillLevel(improvingMaxHP);
            if (slevel > 0) {
                maxhp += improvingMaxHP.getEffect(slevel).getX();
            }
            maxhp += Randomizer.rand(24, 28);
            maxmp += Randomizer.rand(4, 6);
        } else if (job >= 1200 && job <= 1211) { // Flame Wizard
            final ISkill improvingMaxMP = SkillFactory.getSkill(12000000);
            final int slevel = getSkillLevel(improvingMaxMP);
            if (slevel > 0) {
                maxmp += improvingMaxMP.getEffect(slevel).getX() * 2;
            }
            maxhp += Randomizer.rand(10, 14);
            maxmp += Randomizer.rand(22, 24);
        } else if (job >= 1500 && job <= 1512) { // Pirate
            final ISkill improvingMaxHP = SkillFactory.getSkill(15100000);
            final int slevel = getSkillLevel(improvingMaxHP);
            if (slevel > 0) {
                maxhp += improvingMaxHP.getEffect(slevel).getX();
            }
            maxhp += Randomizer.rand(22, 26);
            maxmp += Randomizer.rand(18, 22);
        } else if (job >= 2100 && job <= 2112) { // Aran
            maxhp += Randomizer.rand(50, 52);
            maxmp += Randomizer.rand(4, 6);
        } else if (job >= 2200 && job <= 2218) { // Evan
            maxhp += Randomizer.rand(12, 16);
            maxmp += Randomizer.rand(50, 52);
        } else { // GameMaster
            maxhp += Randomizer.rand(50, 100);
            maxmp += Randomizer.rand(50, 100);
        }
        maxmp += stats.getTotalInt() / 10;

        exp -= GameConstants.getExpNeededForLevel(level);
        level += 1;

        if (level == 200 && !isGM()) {
            final StringBuilder sb = new StringBuilder("[恭喜] ");
            final IItem medal = getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -21);
            if (medal != null) { // Medal
                sb.append("<");
                sb.append(MapleItemInformationProvider.getInstance().getName(medal.getItemId()));
                sb.append("> ");
            }
            sb.append(getName());
            sb.append(" 達到了等級200級！請大家一起恭喜他！");
            World.Broadcast.broadcastMessage(MaplePacketCreator.getItemNotice(sb.toString()).getBytes());
        }
        if (GameConstants.isKOC(job) && level == 120 && !isGM()) {
            final StringBuilder sb = new StringBuilder("[恭喜] ");
            final IItem medal = getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -21);
            if (medal != null) { // Medal
                sb.append("<");
                sb.append(MapleItemInformationProvider.getInstance().getName(medal.getItemId()));
                sb.append("> ");
            }
            sb.append(getName());
            sb.append(" 達到了皇家騎士團峰頂等級120級！請大家一起恭喜他！");
            World.Broadcast.broadcastMessage(MaplePacketCreator.getItemNotice(sb.toString()).getBytes());
        }

        maxhp = (short) Math.min(30000, Math.abs(maxhp));
        maxmp = (short) Math.min(30000, Math.abs(maxmp));

        final List<Pair<MapleStat, Integer>> statup = new ArrayList<>(8);

        statup.add(new Pair<>(MapleStat.MAXHP, maxhp));
        statup.add(new Pair<>(MapleStat.MAXMP, maxmp));
        statup.add(new Pair<>(MapleStat.HP, maxhp));
        statup.add(new Pair<>(MapleStat.MP, maxmp));
        statup.add(new Pair<>(MapleStat.EXP, exp));
        statup.add(new Pair<>(MapleStat.LEVEL, (int) level));

        if (isGM() || (job != 0 && job != 1000 && job != 2000 && job != 2001 && job != 3000)) { // Not Beginner, Nobless and Legend
            remainingSp[GameConstants.getSkillBook(this.job)] += 3;
            client.sendPacket(MaplePacketCreator.updateSp(this, false));
        } else if (level <= 10) {
            stats.setStr((short) (stats.getStr() + remainingAp));
            remainingAp = 0;
            statup.add(new Pair<>(MapleStat.STR, (int) stats.getStr()));
        }

        statup.add(new Pair<>(MapleStat.AVAILABLEAP, (int) remainingAp));

        stats.setMaxHp((short) maxhp);
        stats.setMaxMp((short) maxmp);
        stats.setHp((short) maxhp);
        stats.setMp((short) maxmp);
        client.sendPacket(MaplePacketCreator.updatePlayerStats(statup, getJob()));
        map.broadcastMessage(this, MaplePacketCreator.showForeignEffect(getId(), 0), false);
        stats.recalcLocalStats();
        silentPartyUpdate();
        guildUpdate();
        familyUpdate();
        if (GameConstants.isAran(job)) {
            switch (level) {
                case 30:
                    client.sendPacket(MaplePacketCreator.startMapEffect("恭喜達到30等請回瑞恩島二轉吧。", 5120000, true));
                    break;
                case 70:
                    client.sendPacket(MaplePacketCreator.startMapEffect("恭喜達到70等請到冰原雪域長老公館三轉吧。", 5120000, true));
                    break;
                case 120:
                    client.sendPacket(MaplePacketCreator.startMapEffect("恭喜達到120等請回神木村祭司森林四轉吧。", 5120000, true));
                    break;
            }
        }
        if (GameConstants.isKOC(job) && level == 70) {
            client.sendPacket(MaplePacketCreator.startMapEffect("恭喜達到70等請回耶雷弗三轉吧。", 5120000, true));
        }

    }

    public void changeKeybinding(int key, byte type, int action) {
        if (type != 0) {
            keylayout.Layout().put(key, new Pair<>(type, action));
        } else {
            keylayout.Layout().remove(key);
        }
    }

    public void sendMacros() {
        for (int i = 0; i < 5; i++) {
            if (skillMacros[i] != null) {
                client.sendPacket(MaplePacketCreator.getMacros(skillMacros));
                break;
            }
        }
    }

    public void updateMacros(int position, SkillMacro updateMacro) {
        skillMacros[position] = updateMacro;
    }

    public final SkillMacro[] getMacros() {
        return skillMacros;
    }

    public void tempban(String reason, Calendar duration, int greason, boolean bandIp) {

        try {
            Connection con = DatabaseConnection.getConnection();
            try (PreparedStatement ps = con.prepareStatement("UPDATE accounts SET tempban = ?, banreason = ?, greason = ? WHERE id = ?")) {
                Timestamp TS = new Timestamp(duration.getTimeInMillis());
                ps.setTimestamp(1, TS);
                ps.setString(2, reason);
                ps.setInt(3, greason);
                ps.setInt(4, accountid);
                ps.execute();
            }
            client.disconnect(true, false);
        } catch (SQLException ex) {
            System.err.println("Error while tempbanning" + ex);
        }

    }

    public final boolean ban(String reason, boolean banIP, boolean autoban, boolean hellban) {
        if (lastmonthfameids == null) {
            throw new RuntimeException("Trying to ban a non-loaded character (testhack)");
        }
        String ip = client.getSessionIPAddress();
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("UPDATE accounts SET banned = ?, banreason = ? WHERE id = ?");
            ps.setInt(1, autoban ? 2 : 1);
            ps.setString(2, reason);
            ps.setInt(3, accountid);
            ps.execute();
            ps.close();

            if (banIP) {
                ps = con.prepareStatement("INSERT INTO ipbans (ip) VALUES (?)");
                ps.setString(1, ip);
                ps.executeUpdate();
                ps.close();
                try {
                    for (ChannelServer cs : ChannelServer.getAllInstances()) {
                        for (MapleCharacter chr : cs.getPlayerStorage().getAllCharactersThreadSafe()) {
                            if (chr.getClient().getSessionIPAddress().equals(client.getSessionIPAddress())) {
                                if (!chr.getClient().isGm()) {
                                    chr.getClient().disconnect(true, false);
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                }
                client.banMacs();
                if (hellban) {
                    try (PreparedStatement psa = con.prepareStatement("SELECT * FROM accounts WHERE id = ?")) {
                        psa.setInt(1, accountid);
                        try (ResultSet rsa = psa.executeQuery()) {
                            if (rsa.next()) {
                                try (PreparedStatement pss = con.prepareStatement("UPDATE accounts SET banned = ?, banreason = ? WHERE email = ? OR SessionIP = ?")) {
                                    pss.setInt(1, autoban ? 2 : 1);
                                    pss.setString(2, reason);
                                    pss.setString(3, rsa.getString("email"));
                                    pss.setString(4, client.getSessionIPAddress());
                                    pss.execute();
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            System.err.println("Error while banning" + ex);
            return false;
        }
        client.disconnect(true, false);
        return true;
    }

    public static boolean ban(String id, String reason, boolean accountId, int gmlevel, boolean hellban) {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps;
            if (id.matches("/[0-9]{1,3}\\..*")) {
                ps = con.prepareStatement("INSERT INTO ipbans VALUES (DEFAULT, ?)");
                ps.setString(1, id);
                ps.execute();
                ps.close();
                return true;
            }
            if (accountId) {
                ps = con.prepareStatement("SELECT id FROM accounts WHERE name = ?");
            } else {
                ps = con.prepareStatement("SELECT accountid FROM characters WHERE name = ?");
            }
            boolean ret = false;
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int z = rs.getInt(1);
                    try (PreparedStatement psb = con.prepareStatement("UPDATE accounts SET banned = 1, banreason = ? WHERE id = ? AND gm < ?")) {
                        psb.setString(1, reason);
                        psb.setInt(2, z);
                        psb.setInt(3, gmlevel);
                        psb.execute();
                    }

                    if (gmlevel > 100) {
                        try ( //admin ban
                                PreparedStatement psa = con.prepareStatement("SELECT * FROM accounts WHERE id = ?")) {
                            psa.setInt(1, z);
                            try (ResultSet rsa = psa.executeQuery()) {
                                if (rsa.next()) {
                                    String sessionIP = rsa.getString("sessionIP");
                                    if (sessionIP != null && sessionIP.matches("/[0-9]{1,3}\\..*")) {
                                        try (PreparedStatement psz = con.prepareStatement("INSERT INTO ipbans VALUES (DEFAULT, ?)")) {
                                            psz.setString(1, sessionIP);
                                            psz.execute();
                                        }
                                    }
                                    String macData = rsa.getString("macs");
                                    if (macData != null) {
                                        MapleClient.banMacs(macData);
                                    }
                                    if (hellban) {
                                        try (PreparedStatement pss = con.prepareStatement("UPDATE accounts SET banned = 1, banreason = ? WHERE email = ?" + (sessionIP == null ? "" : " OR SessionIP = ?"))) {
                                            pss.setString(1, reason);
                                            pss.setString(2, rsa.getString("email"));
                                            if (sessionIP != null) {
                                                pss.setString(3, sessionIP);
                                            }
                                            pss.execute();
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ret = true;
                }
            }
            ps.close();
            return ret;
        } catch (SQLException ex) {
            System.err.println("Error while banning" + ex);
        }
        return false;
    }

    public boolean OfflineBanByName(String name, String reason) {
        int id = 0;
        try {
            PreparedStatement ps = null;
            Connection con = DatabaseConnection.getConnection();
            Statement stmt = con.createStatement();
            ResultSet rs;
            ps = con.prepareStatement("select id from characters where name = ?");
            ps.setString(1, name);
            rs = ps.executeQuery();
            while (rs.next()) {
                id = rs.getInt("id");
            }
        } catch (Exception ex) {
        }
        if (id == 0) {
            return false;
        }
        return OfflineBanById(id, reason);
    }

    public boolean OfflineBanById(int id, String reason) {
        try {
            Connection con = DatabaseConnection.getConnection();
            Statement stmt = con.createStatement();
            PreparedStatement ps;
            ResultSet rs;
            int z = id;
            int acid = 0;
            String ip = "";
            String mac = "";
            rs = stmt.executeQuery("select accountid from characters where id = " + id);
            while (rs.next()) {
                acid = rs.getInt("accountid");
            }
            if (acid == 0) {
                return false;
            }
            try (PreparedStatement psb = con.prepareStatement("UPDATE accounts SET banned = 1, banreason = ? WHERE id = ?")) {
                psb.setString(1, reason);
                psb.setInt(2, acid);
                psb.execute();
                psb.close();
            }

            rs = stmt.executeQuery("select SessionIP, macs from accounts where id = " + acid);
            while (rs.next()) {
                ip = rs.getString("SessionIP");
                mac = rs.getString("macs");
            }
            ps = con.prepareStatement("INSERT INTO ipbans (ip) VALUES (?)");
            ps.setString(1, ip);
            ps.executeUpdate();
            ps.close();
            try {
                for (ChannelServer cs : ChannelServer.getAllInstances()) {
                    for (MapleCharacter chr : cs.getPlayerStorage().getAllCharactersThreadSafe()) {
                        if (chr.getClient().getSessionIPAddress().equals(ip)) {
                            if (!chr.getClient().isGm()) {
                                chr.getClient().disconnect(true, false);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
            }
            MapleClient.banMacs(mac);
            rs.close();
            stmt.close();
            return true;
        } catch (Exception ex) {
            System.err.println("封鎖出現錯誤 " + ex);
        }
        return false;
    }

    /**
     * Oid of players is always = the cid
     *
     * @return ObjectId
     */
    @Override
    public int getObjectId() {
        return getId();
    }

    /**
     * Throws unsupported operation exception, oid of players is read only
     *
     * @param id
     */
    @Override
    public void setObjectId(int id) {
        throw new UnsupportedOperationException();
    }

    public MapleStorage getStorage() {
        return storage;
    }

    public void addVisibleMapObject(MapleMapObject mo) {
        if (clone) {
            return;
        }
        visibleMapObjectsLock.writeLock().lock();
        try {
            visibleMapObjects.add(mo);
        } finally {
            visibleMapObjectsLock.writeLock().unlock();
        }
    }

    public void removeVisibleMapObject(MapleMapObject mo) {
        if (clone) {
            return;
        }
        visibleMapObjectsLock.writeLock().lock();
        try {
            visibleMapObjects.remove(mo);
        } finally {
            visibleMapObjectsLock.writeLock().unlock();
        }
    }

    public boolean isMapObjectVisible(MapleMapObject mo) {
        visibleMapObjectsLock.readLock().lock();
        try {
            return !clone && visibleMapObjects.contains(mo);
        } finally {
            visibleMapObjectsLock.readLock().unlock();
        }
    }

    public Collection<MapleMapObject> getAndWriteLockVisibleMapObjects() {
        visibleMapObjectsLock.writeLock().lock();
        return visibleMapObjects;
    }

    public void unlockWriteVisibleMapObjects() {
        visibleMapObjectsLock.writeLock().unlock();
    }

    public boolean isAlive() {
        return stats.getHp() > 0;
    }

    public boolean isAdv() {
        return job >= 0 && job < 1000;
    }

    public boolean isKOC() {
        return job >= 1000 && job < 2000;
    }

    public boolean isAran() {
        return job >= 2000 && job <= 2112 && job != 2001;
    }

    @Override
    public void sendDestroyData(MapleClient client) {
        client.sendPacket(MaplePacketCreator.removePlayerFromMap(this.getObjectId()));
        for (final WeakReference<MapleCharacter> chr : clones) {
            if (chr.get() != null) {
                chr.get().sendDestroyData(client);
            }
        }
    }

    @Override
    public void sendSpawnData(MapleClient client) {
        if (client.getPlayer().allowedToTarget(this)) {
            client.sendPacket(MaplePacketCreator.spawnPlayerMapobject(this));
            if (getParty() != null && !isClone()) {
                updatePartyMemberHP();
                receivePartyMemberHP();
            }
            for (final MaplePet pet : pets) {
                if (pet.getSummoned()) {
                    client.sendPacket(PetPacket.showPet(this, pet, false, false));
                }
            }
            for (final WeakReference<MapleCharacter> chr : clones) {
                if (chr.get() != null) {
                    chr.get().sendSpawnData(client);
                }
            }

            if (summons != null) {
                for (final MapleSummon summon : summons.values()) {
                    client.sendPacket(MaplePacketCreator.spawnSummon(summon, false));
                }
            }
            if (followid > 0) {
                client.sendPacket(MaplePacketCreator.followEffect(followinitiator ? id : followid, followinitiator ? followid : id, null));
            }
        }
    }

    public final void equipChanged() {
        if (map == null) {
            return;
        }
        map.broadcastMessage(this, MaplePacketCreator.updateCharLook(this), false);
        stats.recalcLocalStats();
        if (getMessenger() != null) {
            World.Messenger.updateMessenger(getMessenger().getId(), getName(), client.getChannel());
        }
    }

    public final MaplePet getPet(final int index) {
        byte count = 0;
        for (final MaplePet pet : pets) {
            if (pet.getSummoned()) {
                if (count == index) {
                    return pet;
                }
                count++;
            }
        }
        return null;
    }

    public final MaplePet getPetByUID(final int uid) {
        if (pets == null) {
            return null;
        }
        for (final MaplePet pet : pets) {
            if (pet.getSummoned()) {
                if (pet.getUniqueId() == uid) {
                    return pet;
                }
            }
        }
        return null;
    }

    public void removePetCS(MaplePet pet) {
        pets.remove(pet);
    }

    public void addPet(final MaplePet pet) {
        if (pets.contains(pet)) {
            pets.remove(pet);
        }
        pets.add(pet);
        // So that the pet will be at the last
        // Pet index logic :(
    }

    public void removePet(MaplePet pet) {
        pet.setSummoned(0);
        pets.remove(pet);
    }

    public final List<MaplePet> getSummonedPets() {
        List<MaplePet> ret = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ret.add(null);
        }
        for (final MaplePet pet : pets) {
            if (pet != null && pet.getSummoned()) {
                int index = pet.getSummonedValue() - 1;
                ret.remove(index);
                ret.add(index, pet);
            }
        }
        List<Integer> nullArr = new ArrayList();
        nullArr.add(null);
        ret.removeAll(nullArr);
        return ret;
    }

    public final MaplePet getSummonedPet(final int index) {
        for (final MaplePet pet : getSummonedPets()) {
            if (pet.getSummonedValue() - 1 == index) {
                return pet;
            }
        }
        return null;
    }

    public final void shiftPetsRight() {
        List<MaplePet> petsz = getSummonedPets();
        if (petsz.size() >= 3 || petsz.size() < 1) {
            return;
        } else {
            boolean[] indexBool = new boolean[]{false, false, false};
            for (int i = 0; i < 3; i++) {
                for (MaplePet p : petsz) {
                    if (p.getSummonedValue() == i + 1) {
                        indexBool[i] = true;
                    }
                }
            }
            if (petsz.size() > 1) {
                if (!indexBool[2]) {
                    petsz.get(0).setSummoned(2);
                    petsz.get(1).setSummoned(3);
                } else if (!indexBool[1]) {
                    petsz.get(0).setSummoned(2);
                }
            } else if (indexBool[0]) {
                petsz.get(0).setSummoned(2);
            }
        }
    }

    public final int getPetSlotNext() {
        List<MaplePet> petsz = getSummonedPets();
        int index = 0;
        if (petsz.size() >= 3) {
            unequipPet(getSummonedPet(0), false);
        } else {
            boolean[] indexBool = new boolean[]{false, false, false};
            for (int i = 0; i < 3; i++) {
                for (MaplePet p : petsz) {
                    if (p.getSummonedValue() == i + 1) {
                        indexBool[i] = true;
                    }
                }
            }
            for (boolean b : indexBool) {
                if (!b) {
                    break;
                }
                index++;
            }
            index = Math.min(index, 2);
            for (MaplePet p : petsz) {
                if (p.getSummonedValue() == index + 1) {
                    unequipPet(p, false);
                }
            }
        }
        return index;
    }

    public final byte getPetIndex(final MaplePet petz) {
        return (byte) Math.max(-1, petz.getSummonedValue() - 1);
    }

    public final byte getPetIndex(final int petId) {
        for (final MaplePet pet : getSummonedPets()) {
            if (pet.getUniqueId() == petId) {
                return (byte) Math.max(-1, pet.getSummonedValue() - 1);
            }
        }
        return -1;
    }

    public final byte getPetIndexById(final int petId) {
        for (final MaplePet pet : getSummonedPets()) {
            if (pet.getPetItemId() == petId) {
                return (byte) Math.max(-1, pet.getSummonedValue() - 1);
            }
        }
        return -1;
    }

    public final List<MaplePet> getPets() {
        return pets;
    }

    public final void unequipAllPets() {
        for (final MaplePet pet : getSummonedPets()) {
            unequipPet(pet, false);
        }
    }

    public void unequipPet(MaplePet pet, boolean hunger) {
        if (pet.getSummoned()) {
            pet.saveToDb();
            map.broadcastMessage(this, PetPacket.showPet(this, pet, true, hunger), true);
            //List<Pair<MapleStat, Integer>> stats = new ArrayList<Pair<MapleStat, Integer>>();
            //stats.add(new Pair<MapleStat, Integer>(MapleStat.PET, Integer.valueOf(0)));
            client.sendPacket(PetPacket.petStatUpdate(this));
            if (map != null) {
                map.broadcastMessage(this, PetPacket.showPet(this, pet, true, hunger), true);
            }
            pet.setSummoned(0);
            client.sendPacket(MaplePacketCreator.enableActions());
        }
    }

    public final void unequips() {
        MapleInventory equipped = client.getPlayer().getInventory(MapleInventoryType.EQUIPPED);
        MapleInventory equip = client.getPlayer().getInventory(MapleInventoryType.EQUIP);
        List<Short> ids = new LinkedList<>();
        for (IItem item : equipped.list()) {
            ids.add(item.getPosition());
        }
        for (short id : ids) {
            MapleInventoryManipulator.unequip(client, id, equip.getNextFreeSlot());
        }
    }

    public final long getLastFameTime() {
        return lastfametime;
    }

    public final List<Integer> getFamedCharacters() {
        return lastmonthfameids;
    }

    public FameStatus canGiveFame(MapleCharacter from) {
        if (lastfametime >= System.currentTimeMillis() - 60 * 60 * 24 * 1000) {
            return FameStatus.NOT_TODAY;
        } else if (from == null || lastmonthfameids == null || lastmonthfameids.contains(from.getId())) {
            return FameStatus.NOT_THIS_MONTH;
        }
        return FameStatus.OK;
    }

    public void hasGivenFame(MapleCharacter to) {
        lastfametime = System.currentTimeMillis();
        lastmonthfameids.add(to.getId());
        Connection con = DatabaseConnection.getConnection();

        try (PreparedStatement ps = con.prepareStatement("INSERT INTO famelog (characterid, characterid_to) VALUES (?, ?)")) {
            ps.setInt(1, getId());
            ps.setInt(2, to.getId());
            ps.execute();

        } catch (SQLException e) {
            System.err.println("ERROR writing famelog for char " + getName() + " to " + to.getName() + e);
        }
    }

    public final MapleKeyLayout getKeyLayout() {
        return this.keylayout;
    }

    public MapleParty getParty() {
        return party;
    }

    public int getPartyId() {
        return (party != null ? party.getId() : -1);
    }

    public byte getWorld() {
        return world;
    }

    public void setWorld(byte world) {
        this.world = world;
    }

    public void setParty(MapleParty party) {
        this.party = party;
    }

    public MapleTrade getTrade() {
        return trade;
    }

    public void setTrade(MapleTrade trade) {
        this.trade = trade;
    }

    public EventInstanceManager getEventInstance() {
        return eventInstance;
    }

    public void setEventInstance(EventInstanceManager eventInstance) {
        if(this.eventInstance == eventInstance)
            return;
        EventInstanceManager eim = this.eventInstance;
        this.eventInstance = eventInstance;
        if (eim != null && eim.hasPlayer(this)) {
            eim.unregisterPlayer(this);
        }
    }

    public void addDoor(MapleDoor door) {
        doors.add(door);
    }

    public void clearDoors() {
        doors.clear();
    }

    public List<MapleDoor> getDoors() {
        return new ArrayList<>(doors);
    }

    public void setSmega() {
        if (smega) {
            smega = false;
            dropMessage(5, "你已經禁止廣播訊息。");
        } else {
            smega = true;
            dropMessage(5, "你已經啟用廣播訊息。");
        }
    }

    public boolean getSmega() {
        return smega;
    }

    public Map<Integer, MapleSummon> getSummons() {
        return summons;
    }

    public int getChair() {
        return chair;
    }

    public int getItemEffect() {
        return itemEffect;
    }

    public void setChair(int chair) {
        this.chair = chair;
        stats.relocHeal();
    }

    public void setItemEffect(int itemEffect) {
        this.itemEffect = itemEffect;
    }

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.PLAYER;
    }

    public int getFamilyId() {
        if (mfc == null) {
            return 0;
        }
        return mfc.getFamilyId();
    }

    public int getSeniorId() {
        if (mfc == null) {
            return 0;
        }
        return mfc.getSeniorId();
    }

    public int getJunior1() {
        if (mfc == null) {
            return 0;
        }
        return mfc.getJunior1();
    }

    public int getJunior2() {
        if (mfc == null) {
            return 0;
        }
        return mfc.getJunior2();
    }

    public int getCurrentRep() {
        return currentrep;
    }

    public int getTotalRep() {
        return totalrep;
    }

    public void setCurrentRep(int _rank) {
        currentrep = _rank;
        if (mfc != null) {
            mfc.setCurrentRep(_rank);
        }
    }

    public void setTotalRep(int _rank) {
        totalrep = _rank;
        if (mfc != null) {
            mfc.setTotalRep(_rank);
        }
    }

    public int getGuildId() {
        return guildid;
    }

    public byte getGuildRank() {
        return guildrank;
    }

    public void setGuildId(int _id) {
        guildid = _id;
        if (guildid > 0) {
            if (mgc == null) {
                mgc = new MapleGuildCharacter(this);

            } else {
                mgc.setGuildId(guildid);
            }
        } else {
            mgc = null;
        }
    }

    public void setGuildRank(byte _rank) {
        guildrank = _rank;
        if (mgc != null) {
            mgc.setGuildRank(_rank);
        }
    }

    public MapleGuildCharacter getMGC() {
        return mgc;
    }

    public void setAllianceRank(byte rank) {
        allianceRank = rank;
        if (mgc != null) {
            mgc.setAllianceRank(rank);
        }
    }

    public byte getAllianceRank() {
        return allianceRank;
    }

    public MapleGuild getGuild() {
        if (getGuildId() <= 0) {
            return null;
        }
        return World.Guild.getGuild(getGuildId());
    }

    public void guildUpdate() {
        if (guildid <= 0) {
            return;
        }
        mgc.setLevel((short) level);
        mgc.setJobId(job);
        World.Guild.memberLevelJobUpdate(mgc);
    }

    public void saveGuildStatus() {
        MapleGuild.saveCharacterGuildInfo(guildid, guildrank, allianceRank, id);
    }

    public void familyUpdate() {
        if (mfc == null) {
            return;
        }
        World.Family.memberFamilyUpdate(mfc, this);
    }

    public void saveFamilyStatus() {

        Connection con = DatabaseConnection.getConnection();
        try (PreparedStatement ps = con.prepareStatement("UPDATE characters SET familyid = ?, seniorid = ?, junior1 = ?, junior2 = ? WHERE id = ?")) {
            if (mfc == null) {
                ps.setInt(1, 0);
                ps.setInt(2, 0);
                ps.setInt(3, 0);
                ps.setInt(4, 0);
            } else {
                ps.setInt(1, mfc.getFamilyId());
                ps.setInt(2, mfc.getSeniorId());
                ps.setInt(3, mfc.getJunior1());
                ps.setInt(4, mfc.getJunior2());
            }
            ps.setInt(5, id);
            ps.execute();
        } catch (SQLException se) {
            FilePrinter.printError("MapleCharacter.txt", se, "saveFamilyStatus");
        }
        //MapleFamily.setOfflineFamilyStatus(familyid, seniorid, junior1, junior2, currentrep, totalrep, id);
    }

    public void modifyCSPoints(int type, int quantity) {
        modifyCSPoints(type, quantity, false);
    }

    public void dropMessage(String message) {
        dropMessage(6, message);
    }

    public boolean reloadCSPoints() {
        Connection con = DatabaseConnection.getConnection();
        ResultSet rs;
        try (PreparedStatement ps = con.prepareStatement("SELECT `ACash` , `mPoints` , `points` , `vpoints` FROM accounts where id = ?")) {
            ps.setInt(1, this.getAccountID());
            rs = ps.executeQuery();
            if (rs.next()) {
                this.acash = rs.getInt("ACash");
                this.maplepoints = rs.getInt("mPoints");
                this.points = rs.getInt("points");
                this.vpoints = rs.getInt("vpoints");
            }
            rs.close();
            ps.close();
        } catch (SQLException ex) {
            FilePrinter.printError("CashPoint.txt", this.getName() + "點數讀取異常，目前有 ACash:"
                    + this.getCSPoints(1)
                    + " points:" + this.getPoints()
                    + " mpoints:" + this.getCSPoints(2)
                    + " vpoints" + this.getVPoints());
            return false;
        }
        return true;
    }

    public boolean saveCSPoints() {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = null;
            ps = con.prepareStatement("UPDATE accounts SET `ACash` = ?, `mPoints` = ?, `points` = ?, `vpoints` = ? WHERE id = ?");
            ps.setInt(1, acash);
            ps.setInt(2, maplepoints);
            ps.setInt(3, points);
            ps.setInt(4, vpoints);
            ps.setInt(5, client.getAccID());
            ps.execute();
            ps.close();
        } catch (SQLException ex) {
            FilePrinter.printError("CashPoint.txt", this.getName() + "點數儲存異常，目前有 ACash:"
                    + this.getCSPoints(1)
                    + " points:" + this.getPoints()
                    + " mpoints:" + this.getCSPoints(2)
                    + " vpoints" + this.getVPoints());
            return false;
        }
        return true;
    }

    public void modifyCSPoints(int type, int quantity, boolean show) {
        reloadCSPoints();
        switch (type) {
            case 1:
                if (acash + quantity < 0) {
                    if (show) {
                        dropMessage(-1, "你已經不能再獲得更多點數");
                    }
                    return;
                }
                acash += quantity;
                break;
            case 2:
                if (maplepoints + quantity < 0) {
                    if (show) {
                        dropMessage(-1, "你已經不能再獲得更多楓葉點數");
                    }
                    return;
                }
                maplepoints += quantity;
                break;
            default:
                break;
        }
        if (show && quantity != 0) {
            dropMessage("您已經 " + (quantity > 0 ? "獲得 " : "花費 ") + quantity + (type == 1 ? " GASH點數." : " 楓葉點數."));
            //client.sendPacket(MaplePacketCreator.showSpecialEffect(19));
        }
        saveCSPoints();

    }

    /*
     * @param type 1 = GASH點數 2 = 楓葉點數
     */
    public int getCSPoints(int type) {
        switch (type) {
            case 1:
                return acash;
            case 2:
                return maplepoints;
            default:
                return 0;
        }
    }

    public final boolean hasEquipped(int itemid) {
        return inventory[MapleInventoryType.EQUIPPED.ordinal()].countById(itemid) >= 1;
    }

    public final boolean haveItem(int itemid, int quantity, boolean checkEquipped, boolean greaterOrEquals) {
        final MapleInventoryType type = GameConstants.getInventoryType(itemid);
        int possesed = inventory[type.ordinal()].countById(itemid);
        if (checkEquipped && type == MapleInventoryType.EQUIP) {
            possesed += inventory[MapleInventoryType.EQUIPPED.ordinal()].countById(itemid);
        }
        if (greaterOrEquals) {
            return possesed >= quantity;
        } else {
            return possesed == quantity;
        }
    }

    public final boolean haveItem(int itemid, int quantity) {
        return haveItem(itemid, quantity, true, true);
    }

    public final boolean haveItem(int itemid) {
        return haveItem(itemid, 1, true, true);
    }

    public void dropNPC(String message) {
        client.sendPacket(MaplePacketCreator.getNPCTalk(9010000, (byte) 0, message, "00 00", (byte) 0));
    }

    public void dropNPC(int npc, String message) {
        client.sendPacket(MaplePacketCreator.getNPCTalk(npc, (byte) 0, message, "00 00", (byte) 0));
    }

    private ItemVac ItemVac;
    public boolean itemVacs = false;

    public boolean getItemVac() {
        return itemVacs;
    }

    public void startItemVac() {
        this.ItemVac = new ItemVac(this);
        this.ItemVac.start();
        itemVacs = true;
    }

    public void stopItemVac() {
        if (itemVacs) {
            this.ItemVac.interrupt();
            itemVacs = false;
        }
    }

    public static enum FameStatus {

        OK, NOT_TODAY, NOT_THIS_MONTH
    }

    public byte getBuddyCapacity() {
        return buddylist.getCapacity();
    }

    public void setBuddyCapacity(byte capacity) {
        buddylist.setCapacity(capacity);
        client.sendPacket(MaplePacketCreator.updateBuddyCapacity(capacity));
    }

    public MapleMessenger getMessenger() {
        return messenger;
    }

    public void setMessenger(MapleMessenger messenger) {
        this.messenger = messenger;
    }

    public void addCooldown(int skillId, long startTime, long length) {
        coolDowns.put(skillId, new MapleCoolDownValueHolder(skillId, startTime, length));
    }

    public void removeCooldown(int skillId) {
        if (coolDowns.containsKey(skillId)) {
            coolDowns.remove(skillId);
        }
    }

    public boolean skillisCooling(int skillId) {
        return coolDowns.containsKey(skillId);
    }

    public void giveCoolDowns(final int skillid, long starttime, long length) {
        addCooldown(skillid, starttime, length);
    }

    public void giveCoolDowns(final List<MapleCoolDownValueHolder> cooldowns) {
        int time;
        if (cooldowns != null) {
            for (MapleCoolDownValueHolder cooldown : cooldowns) {
                coolDowns.put(cooldown.skillId, cooldown);
            }
        } else {

            Connection con = DatabaseConnection.getConnection();
            ResultSet rs;
            try (PreparedStatement ps = con.prepareStatement("SELECT SkillID,StartTime,length FROM skills_cooldowns WHERE charid = ?")) {
                ps.setInt(1, getId());
                rs = ps.executeQuery();
                while (rs.next()) {
                    if (rs.getLong("length") + rs.getLong("StartTime") - System.currentTimeMillis() <= 0) {
                        continue;
                    }
                    giveCoolDowns(rs.getInt("SkillID"), rs.getLong("StartTime"), rs.getLong("length"));
                }

                rs.close();
                deleteWhereCharacterId(con, "DELETE FROM skills_cooldowns WHERE charid = ?");

            } catch (SQLException e) {
                FilePrinter.printError("MapleCharcter.txt", e, "Error while retriving cooldown from SQL storage");
            }
        }
    }

    public List<MapleCoolDownValueHolder> getCooldowns() {
        return new ArrayList<>(coolDowns.values());
    }

    public final List<MapleDiseaseValueHolder> getAllDiseases() {
        return new ArrayList<>(diseases.values());
    }

    public final boolean hasDisease(final MapleDisease dis) {
        return diseases.keySet().contains(dis);
    }

    public void getDiseaseBuff(final MapleDisease disease, MobSkill skill) {
        getDiseaseBuff(disease, skill.getX(), skill.getDuration(), skill.getSkillId(), skill.getSkillLevel());
    }

    public void getDiseaseBuff(final MapleDisease disease, int x, long duration, int skillid, int level) {

        final List<Pair<MapleDisease, Integer>> debuff = Collections.singletonList(new Pair<>(disease, x));

        if ((this.map != null) && (!hasDisease(disease))) {
            if ((disease != MapleDisease.SEDUCE) && (disease != MapleDisease.STUN)
                    && (getBuffedValue(MapleBuffStat.HOLY_SHIELD) != null)) {
                return;
            }
            diseases.put(disease, new MapleDiseaseValueHolder(disease, System.currentTimeMillis(), duration));
            client.sendPacket(MaplePacketCreator.giveDebuff(debuff, skillid, level, (int) duration));
            map.broadcastMessage(this, MaplePacketCreator.giveForeignDebuff(id, debuff, skillid, level), false);

            if (this.isShowDebugInfo()) {
                this.dropMessage(6, "[系統提示] 受到 Disease BUFF : " + disease.name() + " X:" + x + " 持續:" + duration + " 等級: " + level);
            }
        }
    }

    public final void giveSilentDebuff(final List<MapleDiseaseValueHolder> ld) {
        if (ld != null) {
            for (final MapleDiseaseValueHolder disease : ld) {
                diseases.put(disease.disease, disease);
            }
        }
    }

    public void dispelDebuff(MapleDisease debuff) {
        if (hasDisease(debuff)) {
            long mask = debuff.getValue();
            boolean first = debuff.isFirst();
            diseases.remove(debuff);
            client.sendPacket(MaplePacketCreator.cancelDebuff(mask, first));
            map.broadcastMessage(this, MaplePacketCreator.cancelForeignDebuff(id, mask, first), false);
        }
    }

    public void dispelDebuffs() {
        dispelDebuff(MapleDisease.CURSE);
        dispelDebuff(MapleDisease.DARKNESS);
        dispelDebuff(MapleDisease.POISON);
        dispelDebuff(MapleDisease.SEAL);
        dispelDebuff(MapleDisease.STUN);
        dispelDebuff(MapleDisease.WEAKEN);
    }

    public void cancelAllDebuffs() {
        diseases.clear();
    }

    public void setLevel(final short level) {
        this.level = (short) (level - 1);
    }

    public void setLevel1(final short level) {
        this.level = (short) (level);
    }

    public void sendNote(String to, String msg) {
        sendNote(to, msg, 0);
    }

    public void sendNote(String to, String msg, int fame) {
        MapleCharacterUtil.sendNote(to, getName(), msg, fame);
    }

    public void showNote() {

        Connection con = DatabaseConnection.getConnection();
        try (PreparedStatement ps = con.prepareStatement("SELECT * FROM notes WHERE `to`=?", ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
            ps.setString(1, getName());
            try (ResultSet rs = ps.executeQuery()) {
                rs.last();
                int count = rs.getRow();
                rs.first();
                client.sendPacket(MTSCSPacket.showNotes(rs, count));
            }
        } catch (SQLException e) {
            FilePrinter.printError("MapleCharacter.txt", e, "Unable to show note");
        }
    }

    public void deleteNote(int id, int fame) {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT gift FROM notes WHERE `id`=?");
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                if (rs.getInt("gift") == fame && fame > 0) { //not exploited! hurray
                    addFame(fame);
                    updateSingleStat(MapleStat.FAME, getFame());
                    client.sendPacket(MaplePacketCreator.getShowFameGain(fame));
                }
            }
            rs.close();
            ps.close();
            ps = con.prepareStatement("DELETE FROM notes WHERE `id`=?");
            ps.setInt(1, id);
            ps.execute();
            ps.close();
        } catch (SQLException e) {
            System.err.println("Unable to delete note" + e);
        }
    }

    public int getmulungEnergy() {
        return mulung_energy;
    }

    public void mulungEnergyModify(boolean inc) {
        if (inc) {
            if (mulung_energy + 100 > 10000) {
                mulung_energy = 10000;
            } else {
                mulung_energy += 100;
            }
        } else {
            mulung_energy = 0;
        }
        client.sendPacket(MaplePacketCreator.MulungEnergy(mulung_energy));
    }

    public void writeMulungEnergy() {
        client.sendPacket(MaplePacketCreator.MulungEnergy(mulung_energy));
    }

    public void writeEnergy(String type, String inc) {
        client.sendPacket(MaplePacketCreator.sendPyramidEnergy(type, inc));
    }

    public void writeStatus(String type, String inc) {
        client.sendPacket(MaplePacketCreator.sendGhostStatus(type, inc));
    }

    public void writePoint(String type, String inc) {
        client.sendPacket(MaplePacketCreator.sendGhostPoint(type, inc));
    }

    public final short getCombo() {
        return combo;
    }

    public void setCombo(final short combo) {
        this.combo = combo;
    }

    public final long getLastComboTime() {
        return lastCombo;
    }

    public void setLastCombo(final long lastComboTime) {
        this.lastCombo = lastComboTime;
    }

    public final long getKeyDownSkillTime() {
        return keydownSkillTime;
    }

    public void setKeyDownSkill_Time(final long keydownSkillTime) {
        this.keydownSkillTime = keydownSkillTime;
    }

    public void checkBerserk() {
        if (BerserkSchedule != null) {
            BerserkSchedule.cancel(false);
            BerserkSchedule = null;
        }

        final ISkill BerserkX = SkillFactory.getSkill(1320006);
        final int skilllevel = getSkillLevel(BerserkX);
        if (skilllevel >= 1) {
            final MapleStatEffect ampStat = BerserkX.getEffect(skilllevel);
            stats.Berserk = stats.getHp() * 100 / stats.getMaxHp() <= ampStat.getX();
            client.sendPacket(MaplePacketCreator.showOwnBuffEffect(1320006, 1, (byte) (stats.Berserk ? 1 : 0)));
            //map.broadcastMessage(this, MaplePacketCreator.showBuffeffect(getId(), 1320006, 1, (byte) (stats.Berserk ? 1 : 0)), false);

            BerserkSchedule = BuffTimer.getInstance().schedule(new Runnable() {
                @Override
                public void run() {
                    checkBerserk();
                }
            }, 10000);
        }
    }

    public void setChalkBoardText(String chalkBoardText) {
        this.chalkBoardText = chalkBoardText;
        this.getMap().broadcastMessage(MTSCSPacket.useChalkboard(getId(), chalkBoardText));
    }

    public String getChalkboard() {
        return chalkBoardText;
    }

    public MapleMount getMount() {
        return mount;
    }

    public int gmLevel() {
        return gmLevel;
    }

    public int[] getWishlist() {
        return wishlist;
    }

    public void clearWishlist() {
        for (int i = 0; i < 10; i++) {
            wishlist[i] = 0;
        }
    }

    public int getWishlistSize() {
        int ret = 0;
        for (int i = 0; i < 10; i++) {
            if (wishlist[i] > 0) {
                ret++;
            }
        }
        return ret;
    }

    public void setWishlist(int[] wl) {
        this.wishlist = wl;
    }

    public int[] getRocks() {
        return rocks;
    }

    public int getRockSize() {
        int ret = 0;
        for (int i = 0; i < 10; i++) {
            if (rocks[i] != 999999999) {
                ret++;
            }
        }
        return ret;
    }

    public void deleteFromRocks(int map) {
        for (int i = 0; i < 10; i++) {
            if (rocks[i] == map) {
                rocks[i] = 999999999;
                break;
            }
        }
    }

    public void addRockMap() {
        if (getRockSize() >= 10) {
            return;
        }
        rocks[getRockSize()] = getMapId();
    }

    public boolean isRockMap(int id) {
        for (int i = 0; i < 10; i++) {
            if (rocks[i] == id) {
                return true;
            }
        }
        return false;
    }

    public int[] getRegRocks() {
        return regrocks;
    }

    public int getRegRockSize() {
        int ret = 0;
        for (int i = 0; i < 5; i++) {
            if (regrocks[i] != 999999999) {
                ret++;
            }
        }
        return ret;
    }

    public void deleteFromRegRocks(int map) {
        for (int i = 0; i < 5; i++) {
            if (regrocks[i] == map) {
                regrocks[i] = 999999999;
                break;
            }
        }
    }

    public void addRegRockMap() {
        if (getRegRockSize() >= 5) {
            return;
        }
        regrocks[getRegRockSize()] = getMapId();
    }

    public boolean isRegRockMap(int id) {
        for (int i = 0; i < 5; i++) {
            if (regrocks[i] == id) {
                return true;
            }
        }
        return false;
    }

    public List<LifeMovementFragment> getLastRes() {
        return lastres;
    }

    public void setLastRes(List<LifeMovementFragment> lastres) {
        this.lastres = lastres;
    }

    public void setMonsterBookCover(int bookCover) {
        this.bookCover = bookCover;
    }

    public int getMonsterBookCover() {
        return bookCover;
    }

    public int getBossLog(String bossid) {
        Connection con1 = DatabaseConnection.getConnection();
        try {
            int ret_count;
            PreparedStatement ps;
            ps = con1.prepareStatement("select count(*) from bosslog where characterid = ? and bossid = ? and lastattempt >= subtime(current_timestamp, '1 0:0:0.0')");
            ps.setInt(1, id);
            ps.setString(2, bossid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ret_count = rs.getInt(1);
                } else {
                    ret_count = -1;
                }
            }
            ps.close();
            return ret_count;
        } catch (Exception Ex) {
            return -1;
        }
    }

    public void setBossLog(String bossid) {
        Connection con1 = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps;
            ps = con1.prepareStatement("insert into bosslog (characterid, bossid) values (?,?)");
            ps.setInt(1, id);
            ps.setString(2, bossid);
            ps.executeUpdate();
            ps.close();
        } catch (Exception Ex) {
        }
    }

    public int getBossACLog(String bossid) {
        Connection con = DatabaseConnection.getConnection();
        try {
            int ret_count;
            PreparedStatement ps;
            ps = con.prepareStatement("select count(*) from bosslog2 where accountid = ? and bossid = ? and lastattempt >= subtime(current_timestamp, '1 0:0:0.0')");
            ps.setInt(1, accountid);
            ps.setString(2, bossid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ret_count = rs.getInt(1);
                } else {
                    ret_count = -1;
                }
            }
            ps.close();
            return ret_count;
        } catch (Exception Ex) {
            return -1;
        }
    }

    public void setBossACLog(String bossid) {
        Connection con1 = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps;
            ps = con1.prepareStatement("insert into bosslog2 (accountid, bossid) values (?,?)");
            ps.setInt(1, accountid);
            ps.setString(2, bossid);
            ps.executeUpdate();
            ps.close();
        } catch (Exception Ex) {
        }
    }

    public int getGainCash() {
        Connection conn = DatabaseConnection.getConnection();
        try {
            int isGained = 1;
            PreparedStatement ps;
            ps = conn.prepareStatement("SELECT gaincash FROM accounts WHERE id=?");
            ps.setInt(1, client.getAccID());
            try (ResultSet rs = ps.executeQuery()) {
                if(rs.next())
                    isGained = rs.getInt(1);
            }
            ps.close();
            return isGained;
        } catch (Exception Ex) {
            return -1;
        }
    }

    public void setGainCash(int n) {
        Connection conn = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps;
            ps = conn.prepareStatement("UPDATE accounts SET gaincash=? WHERE id=?");
            ps.setInt(1, n);
            ps.setInt(2, client.getAccID());
            ps.executeUpdate();
            ps.close();
        } catch (Exception Ex) {
        }
    }

    public void setAllGainCashBack() {
        Connection conn = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps;
            ps = conn.prepareStatement("UPDATE accounts SET gaincash=0");
            ps.executeUpdate();
            ps.close();
        } catch (Exception Ex) {
        }
    }

    public void dropMessage(int type, String message) {
        switch (type) {
            case -1:
                client.sendPacket(UIPacket.getTopMsg(message));
                break;
            case -2:
                client.sendPacket(PlayerShopPacket.shopChat(message, 0)); //0 or what
                break;
            case -3:
                client.sendPacket(MaplePacketCreator.getChatText(getId(), message, isGM(), 0));
                break;
            case -4:
                client.sendPacket(MaplePacketCreator.getChatText(getId(), message, isGM(), 1));
                break;
            case -5:
                client.sendPacket(MaplePacketCreator.showInfo(message));
                break;
            default:
                client.sendPacket(MaplePacketCreator.broadcastMessage(type, message));
        }
    }

    public void toggleTestingDPS() {
        this.testingdps = !testingdps;
    }

    public boolean isTestingDPS() {
        return testingdps;
    }

    public void savePlayer() {
        saveToDB(false, false);
    }

    public void setDPS(long newdps) {
        if (dps >= 2147483647) {
            dps = 0;
        }
        dps = newdps;
    }

    public long getDPS() {
        return dps;
    }

    public IMaplePlayerShop getPlayerShop() {
        return playerShop;
    }

    public void setPlayerShop(IMaplePlayerShop playerShop) {
        this.playerShop = playerShop;
    }

    public int getConversation() {
        return inst.get();
    }

    public void setConversation(int inst) {
        this.inst.set(inst);
    }

    public MapleCarnivalParty getCarnivalParty() {
        return carnivalParty;
    }

    public void setCarnivalParty(MapleCarnivalParty party) {
        carnivalParty = party;
    }

    public void addCP(int ammount) {
        totalCP += ammount;
        availableCP += ammount;
    }

    public void useCP(int ammount) {
        if (availableCP >= ammount) {
            availableCP -= ammount;
        }
    }

    public int getAvailableCP() {
        return availableCP;
    }

    public int getTotalCP() {
        return totalCP;
    }

    public void resetCP() {
        totalCP = 0;
        availableCP = 0;
    }

    public void addCarnivalRequest(MapleCarnivalChallenge request) {
        pendingCarnivalRequests.add(request);
    }

    public final MapleCarnivalChallenge getNextCarnivalRequest() {
        return pendingCarnivalRequests.pollLast();
    }

    public void clearCarnivalRequests() {
        pendingCarnivalRequests = new LinkedList<>();
    }

    public void startMonsterCarnival(final int enemyavailable, final int enemytotal) {
        client.sendPacket(MonsterCarnivalPacket.startMonsterCarnival(this, enemyavailable, enemytotal));
    }

    public void CPUpdate(final boolean party, final int available, final int total, final int team) {
        client.sendPacket(MonsterCarnivalPacket.CPUpdate(party, available, total, team));
    }

    public void playerDiedCPQ(final String name, final int lostCP, final int team) {
        client.sendPacket(MonsterCarnivalPacket.playerDiedMessage(name, lostCP, team));
    }

    public int getEquipLevel(byte slot) {
        MapleInventory equipSlot = getInventory(MapleInventoryType.EQUIP); // Get EQUIP inventory
        int equipId = equipSlot.getItem(slot).getItemId(); // Get ID of the EQUIP item
        long equipInvId = equipSlot.getItem(slot).getInventoryId(); // Get INVENTORYITEMID of the EQUIP item
        Equip equip = (Equip) equipSlot.findByInventoryId(equipInvId, equipId); // GENERATE the EQUIP item
        return equip.getEquipmentLevel(); // Get the EQUIPMENT LEVEL from the EQUIP item
    }

    public int getEquipExp(byte slot) {
        MapleInventory equipSlot = getInventory(MapleInventoryType.EQUIP); // Get EQUIP inventory
        int equipId = equipSlot.getItem(slot).getItemId(); // Get ID of the EQUIP item
        long equipInvId = equipSlot.getItem(slot).getInventoryId(); // Get INVENTORYITEMID of the EQUIP item
        Equip equip = (Equip) equipSlot.findByInventoryId(equipInvId, equipId); // GENERATE the EQUIP item
        return equip.getEquipmentExp(); // Get the EQUIPMENT EXP from the EQUIP item
    }

    public int getEquipExpNeeded(byte slot) {
        MapleInventory equipSlot = getInventory(MapleInventoryType.EQUIP); // Get EQUIP inventory
        int equipId = equipSlot.getItem(slot).getItemId(); // Get ID of the EQUIP item
        long equipInvId = equipSlot.getItem(slot).getInventoryId(); // Get INVENTORYITEMID of the EQUIP item
        Equip equip = (Equip) equipSlot.findByInventoryId(equipInvId, equipId); // GENERATE the EQUIP item
        return equip.getEquipExpNeeded(equip.getEquipmentLevel()); // Get the EQUIPMENT EXP NEEDED for the EQUIP item
    }

    public boolean getCanTalk() {
        return this.canTalk;
    }

    public void canTalk(boolean talk) {
        this.canTalk = talk;
    }

    public int getEXPMod() {
        return stats.expMod;
    }

    public int getDropMod() {
        return stats.dropMod;
    }

    public int getCashMod() {
        return stats.cashMod;
    }

    public void setPoints(int p) {
        this.points = p;
        /*if (this.points >= 1) {
         finishAchievement(1);
         }*/
    }

    public int getPoints() {
        return points;
    }

    public void setVPoints(int p) {
        this.vpoints = p;
    }

    public int getVPoints() {
        return vpoints;
    }

    public CashShop getCashInventory() {
        return cs;
    }

    public void removeAll(int id) {
        removeAll(id, true);
    }

    public void removeAll(int id, boolean show) {
        MapleInventoryType type = GameConstants.getInventoryType(id);
        int possessed = getInventory(type).countById(id);

        if (possessed > 0) {
            MapleInventoryManipulator.removeById(getClient(), type, id, possessed, true, false);
            if (show) {
                getClient().sendPacket(MaplePacketCreator.getShowItemGain(id, (short) -possessed, true));
            }
        }
        /*if (type == MapleInventoryType.EQUIP) { //check equipped
         type = MapleInventoryType.EQUIPPED;
         possessed = getInventory(type).countById(id);

         if (possessed > 0) {
         MapleInventoryManipulator.removeById(getClient(), type, id, possessed, true, false);
         getClient().sendPacket(MaplePacketCreator.getShowItemGain(id, (short)-possessed, true));
         }
         }*/
    }

    public MapleRing getMarriageRing(boolean incluedEquip) {
        MapleInventory iv = getInventory(MapleInventoryType.EQUIPPED);
        Collection<IItem> equippedC = iv.list();
        List<Item> equipped = new ArrayList<>(equippedC.size());
        MapleRing ring;
        for (IItem item : equippedC) {
            equipped.add((Item) item);
        }
        for (Item item : equipped) {
            if (item.getRing() != null) {
                ring = item.getRing();
                ring.setEquipped(true);
                if (GameConstants.isMarriageRing(item.getItemId())) {
                    return ring;
                }
            }
        }
        if (incluedEquip) {
            iv = getInventory(MapleInventoryType.EQUIP);
            for (IItem item : iv.list()) {
                if (item.getRing() != null && GameConstants.isMarriageRing(item.getItemId())) {
                    ring = item.getRing();
                    ring.setEquipped(false);
                    return ring;
                }
            }
        }
        return null;
    }

    //TODO: more than one crush/friendship ring at a time
    public Pair<List<MapleRing>, List<MapleRing>> getRings(boolean equip) {
        MapleInventory iv = getInventory(MapleInventoryType.EQUIPPED);
        Collection<IItem> equippedC = iv.list();
        List<Item> equipped = new ArrayList<>(equippedC.size());
        for (IItem item : equippedC) {
            equipped.add((Item) item);
        }
        Collections.sort(equipped);
        List<MapleRing> crings = new ArrayList<>();
        List<MapleRing> frings = new ArrayList<>();
        MapleRing ring;
        for (Item item : equipped) {
            if (item.getRing() != null) {
                ring = item.getRing();
                ring.setEquipped(true);
                if (GameConstants.isEffectRing(item.getItemId())) {
                    if (equip) {
                        if (GameConstants.isCrushRing(item.getItemId())) {
                            crings.add(ring);
                        } else if (GameConstants.isFriendshipRing(item.getItemId())) {
                            frings.add(ring);
                        }
                    } else if (crings.isEmpty() && GameConstants.isCrushRing(item.getItemId())) {
                        crings.add(ring);
                    } else if (frings.isEmpty() && GameConstants.isFriendshipRing(item.getItemId())) {
                        frings.add(ring);
                    } //for 3rd person the actual slot doesnt matter, so we'll use this to have both shirt/ring same?
                    //however there seems to be something else behind this, will have to sniff someone with shirt and ring, or more conveniently 3-4 of those
                }
            }
        }
        if (equip) {
            iv = getInventory(MapleInventoryType.EQUIP);
            for (IItem item : iv.list()) {
                if (item.getRing() != null && GameConstants.isEffectRing(item.getItemId())) {
                    ring = item.getRing();
                    ring.setEquipped(false);
                    if (GameConstants.isFriendshipRing(item.getItemId())) {
                        frings.add(ring);
                    } else if (GameConstants.isCrushRing(item.getItemId())) {
                        crings.add(ring);
                    }
                }
            }
        }
        Collections.sort(frings, new MapleRing.RingComparator());
        Collections.sort(crings, new MapleRing.RingComparator());
        return new Pair<>(crings, frings);
    }

    public int getFH() {
        MapleFoothold fh = getMap().getFootholds().findBelow(getPosition());
        if (fh != null) {
            return fh.getId();
        }
        return 0;
    }

    public void startFairySchedule(boolean exp) {
        startFairySchedule(exp, false);
    }

    public void startFairySchedule(boolean exp, boolean equipped) {
        cancelFairySchedule(exp);
        if (fairyExp < 30 && stats.equippedFairy) {
            if (equipped) {
                dropMessage(5, "您裝備了精靈吊墜在1小時後經驗獲取將增加到 " + (fairyExp + 10) + "%");
            }
            fairySchedule = EtcTimer.getInstance().schedule(new Runnable() {
                @Override
                public void run() {
                    if (fairyExp < 30 && stats.equippedFairy) {
                        fairyExp += 10;
                        fairyHour += 1;
                        dropMessage(5, "因裝備精靈墜飾經過了" + fairyHour + "小時，打怪時可以額外獲得紅利經驗值" + fairyExp + "%.");
                        startFairySchedule(false, true);
                    } else {
                        cancelFairySchedule(!stats.equippedFairy);
                    }
                }
            }, 60 * 60 * 1000);
        } else {
            cancelFairySchedule(!stats.equippedFairy);
        }
    }

    public void cancelFairySchedule(boolean exp) {
        if (fairySchedule != null) {
            fairySchedule.cancel(false);
            fairySchedule = null;
        }
        if (exp) {
            this.fairyExp = 10;
            this.fairyHour = 1;
        }
    }

    public byte getFairyExp() {
        return fairyExp;
    }

    public int getCoconutTeam() {
        return coconutteam;
    }

    public void setCoconutTeam(int team) {
        coconutteam = team;
    }

    public void spawnPet(byte slot) {
        spawnPet(slot, false, true);
    }

    public void spawnPet(byte slot, boolean lead) {
        spawnPet(slot, lead, true);
    }

    public void spawnPet(byte slot, boolean lead, boolean broadcast) {
        final IItem item = getInventory(MapleInventoryType.CASH).getItem(slot);
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        if (item == null || item.getItemId() > 5000100 || item.getItemId() < 5000000) {
            return;
        }
        switch (item.getItemId()) {
            case 5000047:
            case 5000028: {
                final MaplePet pet = MaplePet.createPet(item.getItemId() + 1, MapleInventoryIdentifier.getInstance());
                if (pet != null) {
                    MapleInventoryManipulator.addById(client, item.getItemId() + 1, (short) 1, item.getOwner(), pet, ii.getPetLife(item.getItemId()));
                    MapleInventoryManipulator.removeFromSlot(client, MapleInventoryType.CASH, slot, (short) 1, false);
                }
                break;
            }
            default: {
                final MaplePet pet = item.getPet();
                if (pet != null && (item.getItemId() != 5000054 || pet.getSecondsLeft() > 0) && (item.getExpiration() == -1 || item.getExpiration() > System.currentTimeMillis())) {
                    if (pet.getSummoned()) { // Already summoned, let's keep it
                        unequipPet(pet, false);
                    } else {
                        int leadid = 8;
                        if (GameConstants.isKOC(getJob())) {
                            leadid = 10000018;
                        } else if (GameConstants.isAran(getJob())) {
                            leadid = 20000024;
                        }
                        if (getSkillLevel(SkillFactory.getSkill(leadid)) == 0 && getPet(0) != null) {
                            unequipPet(getPet(0), false);
                        } else if (lead && getSkillLevel(SkillFactory.getSkill(leadid)) > 0) { // Follow the Lead
                            shiftPetsRight();
                        }
                        final Point pos = getPosition();
                        pet.setPos(pos);
                        try {
                            pet.setFh(getMap().getFootholds().findBelow(pos).getId());
                        } catch (NullPointerException e) {
                            pet.setFh(0); //lol, it can be fixed by movement
                        }
                        pet.setStance(0);
                        pet.setSummoned(getPetSlotNext() + 1);

                        addPet(pet);
                        if (broadcast) {
                            getMap().broadcastMessage(this, PetPacket.showPet(this, pet, false, false), true);

                            //final List<Pair<MapleStat, Integer>> stats = new ArrayList<Pair<MapleStat, Integer>>(1);
                            //stats.add(new Pair<MapleStat, Integer>(MapleStat.PET, Integer.valueOf(pet.getUniqueId())));
                            client.sendPacket(PetPacket.petStatUpdate(this));
                        }
                    }
                }
                break;
            }
        }
        client.sendPacket(PetPacket.emptyStatUpdate());
    }

    public void addMoveMob(int mobid) {
        if (movedMobs.containsKey(mobid)) {
            movedMobs.put(mobid, movedMobs.get(mobid) + 1);
            if (movedMobs.get(mobid) > 30) { //trying to move not null monster = broadcast dead
                for (MapleCharacter chr : getMap().getCharactersThreadsafe()) { //also broadcast to others
                    if (chr.getMoveMobs().containsKey(mobid)) { //they also tried to move this mob
                        chr.getClient().sendPacket(MobPacket.killMonster(mobid, 1));
                        chr.getMoveMobs().remove(mobid);
                    }
                }
            }
        } else {
            movedMobs.put(mobid, 1);
        }
    }

    public Map<Integer, Integer> getMoveMobs() {
        return movedMobs;
    }

    public int getLinkMid() {
        return linkMid;
    }

    public void setLinkMid(int lm) {
        this.linkMid = lm;
    }

    public boolean isClone() {
        return clone;
    }

    public void setClone(boolean c) {
        this.clone = c;
    }

    public WeakReference<MapleCharacter>[] getClones() {
        return clones;
    }

    public MapleCharacter cloneLooks() {
        MapleClient cloneClient = new MapleClient(null, null, new MockIOSession());

        final int minus = (getId() + Randomizer.nextInt(getId())); // really randomize it, dont want it to fail

        MapleCharacter ret = new MapleCharacter(true);
        ret.id = minus;
        ret.client = cloneClient;
        ret.exp = 0;
        ret.meso = 0;
        ret.pachinkoBalls = pachinkoBalls;
        ret.blood = blood;
        ret.month = month;
        ret.day = day;
        ret.charmessage = charmessage;
        ret.expression = expression;
        ret.constellation = constellation;
        ret.remainingAp = 0;
        ret.fame = 0;
        ret.accountid = client.getAccID();
        ret.name = name;
        ret.level = level;
        ret.fame = fame;
        ret.job = job;
        ret.hair = hair;
        ret.face = face;
        ret.skinColor = skinColor;
        ret.bookCover = bookCover;
        ret.monsterbook = monsterbook;
        ret.mount = mount;
        ret.CRand = new PlayerRandomStream();
        ret.gmLevel = gmLevel;
        ret.gender = gender;
        ret.mapid = map.getId();
        ret.map = map;
        ret.setStance(getStance());
        ret.chair = chair;
        ret.itemEffect = itemEffect;
        ret.guildid = guildid;
        ret.currentrep = currentrep;
        ret.totalrep = totalrep;
        ret.stats = stats;
        ret.effects.putAll(effects);
        if (ret.effects.get(MapleBuffStat.ILLUSION) != null) {
            ret.effects.remove(MapleBuffStat.ILLUSION);
        }
        if (ret.effects.get(MapleBuffStat.SUMMON) != null) {
            ret.effects.remove(MapleBuffStat.SUMMON);
        }

        if (ret.effects.get(MapleBuffStat.PUPPET) != null) {
            ret.effects.remove(MapleBuffStat.PUPPET);
        }
        ret.guildrank = guildrank;
        ret.allianceRank = allianceRank;
        ret.hidden = hidden;
        ret.setPosition(new Point(getPosition()));
        for (IItem equip : getInventory(MapleInventoryType.EQUIPPED)) {
            ret.getInventory(MapleInventoryType.EQUIPPED).addFromDB(equip);
        }
        ret.skillMacros = skillMacros;
        ret.keylayout = keylayout;
        ret.questinfo = questinfo;
        ret.savedLocations = savedLocations;
        ret.wishlist = wishlist;
        ret.rocks = rocks;
        ret.regrocks = regrocks;
        ret.buddylist = buddylist;
        ret.keydownSkillTime = 0;
        ret.lastmonthfameids = lastmonthfameids;
        ret.lastfametime = lastfametime;
        ret.storage = storage;
        ret.cs = this.cs;
        ret.client.setAccountName(client.getAccountName());
        ret.client.setAccID(this.accountid);
        ret.acash = acash;
        ret.maplepoints = maplepoints;
        ret.clone = true;
        ret.client.setChannel(this.client.getChannel());
        while (map.getCharacterById(ret.id) != null || client.getChannelServer().getPlayerStorage().getCharacterById(ret.id) != null) {
            ret.id++;
        }
        ret.client.setPlayer(ret);
        return ret;
    }

    public final void cloneLook() {
        if (clone) {
            return;
        }
        for (int i = 0; i < clones.length; i++) {
            if (clones[i].get() == null) {
                final MapleCharacter newp = cloneLooks();
                map.addPlayer(newp);
                map.broadcastMessage(MaplePacketCreator.updateCharLook(newp));
                map.movePlayer(newp, getPosition());
                clones[i] = new WeakReference<>(newp);
                return;
            }
        }
    }

    public final void disposeClones() {
        numClones = 0;
        for (int i = 0; i < clones.length; i++) {
            if (clones[i].get() != null) {
                map.removePlayer(clones[i].get());
                clones[i].get().getClient().disconnect(false, false);
                clones[i] = new WeakReference<>(null);
                numClones++;
            }
        }
    }

    public final int getCloneSize() {
        int z = 0;
        for (WeakReference<MapleCharacter> clone1 : clones) {
            if (clone1.get() != null) {
                z++;
            }
        }
        return z;
    }

    public void spawnClones() {
        if (numClones == 0 && stats.hasClone) {
            cloneLook(); //once and never again
        }
        for (int i = 0; i < numClones; i++) {
            cloneLook();
        }
        numClones = 0;
    }

    public byte getNumClones() {
        return numClones;
    }

    public final void spawnSavedPets() {
        for (int i = 0; i < petStore.length; i++) {
            if (petStore[i] > -1) {
                spawnPet(petStore[i], false, false);
            }
        }
        client.sendPacket(PetPacket.petStatUpdate(this));
        petStore = new byte[]{-1, -1, -1};
    }

    public final byte[] getPetStores() {
        return petStore;
    }

    public void resetStats(final int str, final int dex, final int int_, final int luk) {
        List<Pair<MapleStat, Integer>> stat = new ArrayList<>(2);
        int total = stats.getStr() + stats.getDex() + stats.getLuk() + stats.getInt() + getRemainingAp();

        total -= str;
        stats.setStr((short) str);

        total -= dex;
        stats.setDex((short) dex);

        total -= int_;
        stats.setInt((short) int_);

        total -= luk;
        stats.setLuk((short) luk);

        setRemainingAp((short) total);

        stat.add(new Pair<>(MapleStat.STR, str));
        stat.add(new Pair<>(MapleStat.DEX, dex));
        stat.add(new Pair<>(MapleStat.INT, int_));
        stat.add(new Pair<>(MapleStat.LUK, luk));
        stat.add(new Pair<>(MapleStat.AVAILABLEAP, total));
        client.sendPacket(MaplePacketCreator.updatePlayerStats(stat, false, getJob()));
    }

    public Event_PyramidSubway getPyramidSubway() {
        return pyramidSubway;
    }

    public void setPyramidSubway(Event_PyramidSubway ps) {
        this.pyramidSubway = ps;
    }

    public byte getSubcategory() {
        if (job >= 430 && job <= 434) {
            return 1; //dont set it
        }
        return subcategory;
    }

    public int itemQuantity(final int itemid) {
        return getInventory(GameConstants.getInventoryType(itemid)).countById(itemid);
    }

    public void setRPS(RockPaperScissors rps) {
        this.rps = rps;
    }

    public RockPaperScissors getRPS() {
        return rps;
    }

    public long getNextConsume() {
        return nextConsume;
    }

    public void setNextConsume(long nc) {
        this.nextConsume = nc;
    }

    public int getRank() {
        return rank;
    }

    public int getRankMove() {
        return rankMove;
    }

    public int getJobRank() {
        return jobRank;
    }

    public int getJobRankMove() {
        return jobRankMove;
    }

    public void changeChannel(final int channel) {
        final ChannelServer toch = ChannelServer.getInstance(channel);

        int res = this.saveToDB(false, false);
        expirationTask(true, false);
        if (res == 1) {
            dropMessage(5, "角色保存成功！");
        }
        if (client.getPlayer().isTestingDPS()) {
            final MapleMonster mm = MapleLifeFactory.getMonster(9001007);
            map.killMonster1(mm);
            client.getPlayer().toggleTestingDPS();
            client.getPlayer().dropMessage(5, "已停止當前的DPM測試。");
        }

        if (channel == client.getChannel() || toch == null || toch.isShutdown()) {
            client.sendPacket(MaplePacketCreator.serverBlocked(1));
            return;
        }
        changeRemoval();

        final ChannelServer ch = ChannelServer.getInstance(client.getChannel());
        if (getMessenger() != null) {
            World.Messenger.silentLeaveMessenger(getMessenger().getId(), new MapleMessengerCharacter(this));
        }
        PlayerBuffStorage.addBuffsToStorage(getId(), getAllBuffs());
        PlayerBuffStorage.addCooldownsToStorage(getId(), getCooldowns());
        PlayerBuffStorage.addDiseaseToStorage(getId(), getAllDiseases());
        World.channelChangeData(new CharacterTransfer(this), getId(), channel);
        ch.removePlayer(this);
        client.updateLoginState(MapleClient.CHANGE_CHANNEL, client.getSessionIPAddress());
        client.sendPacket(MaplePacketCreator.getChannelChange(toch.getGatewayIP(), toch.getPort()));
        getMap().removePlayer(this);
        client.setPlayer(null);
        client.setReceiving(false);
    }

    public void expandInventory(byte type, int amount) {
        final MapleInventory inv = getInventory(MapleInventoryType.getByType(type));
        inv.addSlot((byte) amount);
        client.sendPacket(MaplePacketCreator.getSlotUpdate(type, (byte) inv.getSlotLimit()));
    }

    public boolean allowedToTarget(MapleCharacter other) {
        return other != null && (!other.isHidden() || getGMLevel() >= other.getGMLevel());
    }

    public int getFollowId() {
        return followid;
    }

    public void setFollowId(int fi) {
        this.followid = fi;
        if (fi == 0) {
            this.followinitiator = false;
            this.followon = false;
        }
    }

    public void setFollowInitiator(boolean fi) {
        this.followinitiator = fi;
    }

    public void setFollowOn(boolean fi) {
        this.followon = fi;
    }

    public boolean isFollowOn() {
        return followon;
    }

    public boolean isFollowInitiator() {
        return followinitiator;
    }

    public void checkFollow() {
        if (followon) {
            map.broadcastMessage(MaplePacketCreator.followEffect(id, 0, null));
            map.broadcastMessage(MaplePacketCreator.followEffect(followid, 0, null));
            MapleCharacter tt = map.getCharacterById(followid);
            client.sendPacket(MaplePacketCreator.getFollowMessage("Follow canceled."));
            if (tt != null) {
                tt.setFollowId(0);
                tt.getClient().sendPacket(MaplePacketCreator.getFollowMessage("Follow canceled."));
            }
            setFollowId(0);
        }
    }

    public int getMarriageId() {
        return marriageId;
    }

    public void setMarriageId(final int mi) {
        this.marriageId = mi;
    }

    public int getMarriageItemId() {
        return marriageItemId;
    }

    public void setMarriageItemId(final int mi) {
        this.marriageItemId = mi;
    }

    public boolean isStaff() {
        return this.gmLevel > ServerConstants.PlayerGMRank.NORMAL.getLevel();
    }

    // TODO: gvup, vic, lose, draw, VR
    public boolean startPartyQuest(final int questid) {
        boolean ret = false;
        if (!quests.containsKey(MapleQuest.getInstance(questid)) || !questinfo.containsKey(questid)) {
            final MapleQuestStatus status = getQuestNAdd(MapleQuest.getInstance(questid));
            status.setStatus((byte) 1);
            updateQuest(status);
            switch (questid) {
                case 1300:
                case 1301:
                case 1302: //carnival, ariants.
                    updateInfoQuest(questid, "min=0;sec=0;date=0000-00-00;have=0;rank=F;try=0;cmp=0;CR=0;VR=0;gvup=0;vic=0;lose=0;draw=0");
                    break;
                case 1204: //herb town pq
                    updateInfoQuest(questid, "min=0;sec=0;date=0000-00-00;have0=0;have1=0;have2=0;have3=0;rank=F;try=0;cmp=0;CR=0;VR=0");
                    break;
                case 1206: //ellin pq
                    updateInfoQuest(questid, "min=0;sec=0;date=0000-00-00;have0=0;have1=0;rank=F;try=0;cmp=0;CR=0;VR=0");
                    break;
                default:
                    updateInfoQuest(questid, "min=0;sec=0;date=0000-00-00;have=0;rank=F;try=0;cmp=0;CR=0;VR=0");
                    break;
            }
            ret = true;
        } //started the quest.
        return ret;
    }

    public String getOneInfo(final int questid, final String key) {
        if (!questinfo.containsKey(questid) || key == null) {
            return null;
        }
        final String[] split = questinfo.get(questid).split(";");
        for (String x : split) {
            final String[] split2 = x.split("="); //should be only 2
            if (split2.length == 2 && split2[0].equals(key)) {
                return split2[1];
            }
        }
        return null;
    }

    public void updateOneInfo(final int questid, final String key, final String value) {
        if (!questinfo.containsKey(questid) || key == null || value == null) {
            return;
        }
        final String[] split = questinfo.get(questid).split(";");
        boolean changed = false;
        final StringBuilder newQuest = new StringBuilder();
        for (String x : split) {
            final String[] split2 = x.split("="); //should be only 2
            if (split2.length != 2) {
                continue;
            }
            if (split2[0].equals(key)) {
                newQuest.append(key).append("=").append(value);
            } else {
                newQuest.append(x);
            }
            newQuest.append(";");
            changed = true;
        }

        updateInfoQuest(questid, changed ? newQuest.toString().substring(0, newQuest.toString().length() - 1) : newQuest.toString());
    }

    public void recalcPartyQuestRank(final int questid) {
        if (!startPartyQuest(questid)) {
            final String oldRank = getOneInfo(questid, "rank");
            if (oldRank == null || oldRank.equals("S")) {
                return;
            }
            String newRank;
            switch (oldRank) {
                case "A":
                    newRank = "S";
                    break;
                case "B":
                    newRank = "A";
                    break;
                case "C":
                    newRank = "B";
                    break;
                case "D":
                    newRank = "C";
                    break;
                case "F":
                    newRank = "D";
                    break;
                default:
                    return;
            }
            final List<Pair<String, Pair<String, Integer>>> questInfo = MapleQuest.getInstance(questid).getInfoByRank(newRank);
            for (Pair<String, Pair<String, Integer>> q : questInfo) {
                boolean found = false;
                final String val = getOneInfo(questid, q.right.left);
                if (val == null) {
                    return;
                }
                int vall;
                try {
                    vall = Integer.parseInt(val);
                } catch (NumberFormatException e) {
                    return;
                }
                switch (q.left) {
                    case "less":
                        found = vall < q.right.right;
                        break;
                    case "more":
                        found = vall > q.right.right;
                        break;
                    case "equal":
                        found = vall == q.right.right;
                        break;
                }
                if (!found) {
                    return;
                }
            }
            //perfectly safe
            updateOneInfo(questid, "rank", newRank);
        }
    }

    public void tryPartyQuest(final int questid) {
        try {
            startPartyQuest(questid);
            pqStartTime = System.currentTimeMillis();
            updateOneInfo(questid, "try", String.valueOf(Integer.parseInt(getOneInfo(questid, "try")) + 1));
        } catch (Exception e) {
            FilePrinter.printError("MapleCharacter.txt", e, "tryPartyQuest error");
        }
    }

    public void endPartyQuest(final int questid) {
        try {
            startPartyQuest(questid);
            if (pqStartTime > 0) {
                final long changeTime = System.currentTimeMillis() - pqStartTime;
                final int mins = (int) (changeTime / 1000 / 60), secs = (int) (changeTime / 1000 % 60);
                final int mins2 = Integer.parseInt(getOneInfo(questid, "min")), secs2 = Integer.parseInt(getOneInfo(questid, "sec"));
                if (mins2 <= 0 || mins < mins2) {
                    updateOneInfo(questid, "min", String.valueOf(mins));
                    updateOneInfo(questid, "sec", String.valueOf(secs));
                    updateOneInfo(questid, "date", FilePrinter.getLocalDateString());
                }
                final int newCmp = Integer.parseInt(getOneInfo(questid, "cmp")) + 1;
                updateOneInfo(questid, "cmp", String.valueOf(newCmp));
                updateOneInfo(questid, "CR", String.valueOf((int) Math.ceil((newCmp * 100.0) / Integer.parseInt(getOneInfo(questid, "try")))));
                recalcPartyQuestRank(questid);
                pqStartTime = 0;
            }
        } catch (Exception e) {
            FilePrinter.printError("MapleCharacter.txt", e, "endPartyQuest error");
        }

    }

    public void havePartyQuest(final int itemId) {
        int questid, index = -1;
        switch (itemId) {
            case 1002798:
                questid = 1200; //henesys
                break;
            case 1072369:
                questid = 1201; //kerning
                break;
            case 1022073:
                questid = 1202; //ludi
                break;
            case 1082232:
                questid = 1203; //orbis
                break;
            case 1002571:
            case 1002572:
            case 1002573:
            case 1002574:
                questid = 1204; //herbtown
                index = itemId - 1002571;
                break;
            case 1122010:
                questid = 1205; //magatia
                break;
            case 1032061:
            case 1032060:
                questid = 1206; //ellin
                index = itemId - 1032060;
                break;
            case 3010018:
                questid = 1300; //ariant
                break;
            case 1122007:
                questid = 1301; //carnival
                break;
            case 1122058:
                questid = 1302; //carnival2
                break;
            default:
                return;
        }
        startPartyQuest(questid);
        updateOneInfo(questid, "have" + (index == -1 ? "" : index), "1");
    }

    public void resetStatsByJob(boolean beginnerJob) {
        int baseJob = (beginnerJob ? (job % 1000) : (job % 1000 / 100 * 100)); //1112 -> 112 -> 1 -> 100
        switch (baseJob) {
            case 100:
                resetStats(25, 4, 4, 4);
                break;
            case 200:
                resetStats(4, 4, 20, 4);
                break;
            case 300:
            case 400:
                resetStats(4, 25, 4, 4);
                break;
            case 500:
                resetStats(4, 20, 4, 4);
                break;
            default:
                break;
        }
    }

    public boolean hasSummon() {
        return hasSummon;
    }

    public void setHasSummon(boolean summ) {
        this.hasSummon = summ;
    }

    public void removeDoor() {
        final MapleDoor door = getDoors().iterator().next();
        for (final MapleCharacter chr : door.getTarget().getCharactersThreadsafe()) {
            door.sendDestroyData(chr.getClient());
        }
        for (final MapleCharacter chr : door.getTown().getCharactersThreadsafe()) {
            door.sendDestroyData(chr.getClient());
        }
        for (final MapleDoor destroyDoor : getDoors()) {
            door.getTarget().removeMapObject(destroyDoor);
            door.getTown().removeMapObject(destroyDoor);
        }
        clearDoors();
    }

    public void changeRemoval() {
        changeRemoval(false);
    }

    public void changeRemoval(boolean dc) {
        if (getTrade() != null) {
            MapleTrade.cancelTrade(getTrade(), client);
        }
        if (getCheatTracker() != null) {
            getCheatTracker().dispose();
        }
        if (!dc) {
            cancelEffectFromBuffStat(MapleBuffStat.MONSTER_RIDING);
            cancelEffectFromBuffStat(MapleBuffStat.SUMMON);
            cancelEffectFromBuffStat(MapleBuffStat.PUPPET);
        }
        if (getPyramidSubway() != null) {
            getPyramidSubway().dispose(this);
        }
        if (playerShop != null && !dc) {
            playerShop.removeVisitor(this);
            if (playerShop.isOwner(this)) {
                playerShop.setOpen(true);
            }
        }
        if (!getDoors().isEmpty()) {
            removeDoor();
        }
        disposeClones();
        NPCScriptManager.getInstance().dispose(client);
    }

    public void updateTick(int newTick) {
        anticheat.updateTick(newTick);
    }

    public boolean canUseFamilyBuff(MapleFamilyBuffEntry buff) {
        final MapleQuestStatus stat = getQuestNAdd(MapleQuest.getInstance(buff.questID));
        if (stat.getCustomData() == null) {
            stat.setCustomData("0");
        }
        return Long.parseLong(stat.getCustomData()) + (24 * 3600000) < System.currentTimeMillis();
    }

    public void useFamilyBuff(MapleFamilyBuffEntry buff) {
        final MapleQuestStatus stat = getQuestNAdd(MapleQuest.getInstance(buff.questID));
        stat.setCustomData(String.valueOf(System.currentTimeMillis()));
    }

    public List<Pair<Integer, Integer>> usedBuffs() {
        //assume count = 1
        List<Pair<Integer, Integer>> used = new ArrayList<>();
        for (MapleFamilyBuffEntry buff : MapleFamilyBuff.getBuffEntry()) {
            if (!canUseFamilyBuff(buff)) {
                used.add(new Pair<>(buff.index, buff.count));
            }
        }
        return used;
    }

    public String getTeleportName() {
        return teleportname;
    }

    public void setTeleportName(final String tname) {
        teleportname = tname;
    }

    public int getNoJuniors() {
        if (mfc == null) {
            return 0;
        }
        return mfc.getNoJuniors();
    }

    public MapleFamilyCharacter getMFC() {
        return mfc;
    }

    public void makeMFC(final int familyid, final int seniorid, final int junior1, final int junior2) {
        if (familyid > 0) {
            MapleFamily f = World.Family.getFamily(familyid);
            if (f == null) {
                mfc = null;
            } else {
                mfc = f.getMFC(id);
                if (mfc == null) {
                    mfc = f.addFamilyMemberInfo(this, seniorid, junior1, junior2);
                }
                if (mfc.getSeniorId() != seniorid) {
                    mfc.setSeniorId(seniorid);
                }
                if (mfc.getJunior1() != junior1) {
                    mfc.setJunior1(junior1);
                }
                if (mfc.getJunior2() != junior2) {
                    mfc.setJunior2(junior2);
                }
            }
        } else {
            mfc = null;
        }
    }

    public void setFamily(final int newf, final int news, final int newj1, final int newj2) {
        if (mfc == null || newf != mfc.getFamilyId() || news != mfc.getSeniorId() || newj1 != mfc.getJunior1() || newj2 != mfc.getJunior2()) {
            makeMFC(newf, news, newj1, newj2);
        }
    }

    public int maxBattleshipHP(int skillid) {
        return (getSkillLevel(skillid) * 5000) + ((getLevel() - 120) * 3000);
    }

    public int currentBattleshipHP() {
        return battleshipHP;
    }

    public int getGachExp() {
        return gachexp;
    }

    public void setGachExp(int ge) {
        this.gachexp = ge;
    }

    public void sendEnglishQuiz(String msg) {
        client.sendPacket(MaplePacketCreator.englishQuizMsg(msg));
    }

    public void fakeRelog() {
        client.sendPacket(MaplePacketCreator.getCharInfo(this));
        final MapleMap mapp = getMap();
        mapp.removePlayer(this);
        mapp.addPlayer(this);
    }

    public boolean canSummon() {
        return canSummon(5000);
    }

    public boolean canSummon(int g) {
        if (lastSummonTime + g < System.currentTimeMillis()) {
            lastSummonTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    public String getcharmessage() {
        //System.err.println("CharMessage(get)");
        return charmessage;
    }

    public void setcharmessage(String s) {
        //System.err.println("CharMessage(set)");
        charmessage /*+*/ = s;
    }

    public int getexpression() {
        return expression;
    }

    public void setexpression(int s) {
        expression /*+*/ = s;
    }

    public int getconstellation() {
        return constellation;
    }

    public void setconstellation(int s) {
        constellation /*+*/ = s;
    }

    public int getblood() {
        return blood;
    }

    public void setblood(int s) {
        blood /*+*/ = s;
    }

    public int getmonth() {
        return month;
    }

    public void setmonth(int s) {
        month /*+*/ = s;
    }

    public int getday() {
        return day;
    }

    public void setday(int s) {
        day /*+*/ = s;
    }

    public int getTeam() {
        return coconutteam;
    }

    public int getBalls() {
        return pachinkoBalls;
    }

    public void gainBalls(int s) {
        pachinkoBalls += s;
        this.getClient().sendPacket(PachinkoPacket.updateBalls(this.getId(), pachinkoBalls));
    }

    public void setBalls(int s) {
        pachinkoBalls = s;
    }

    public int getBeansNum() {
        return beansNum;
    }

    /**
     * @param beansNum the beansNum to set
     */
    public void setBeansNum(int beansNum) {
        this.beansNum = beansNum;
    }

    /**
     * @return the beansRange
     */
    public int getBeansRange() {
        return beansRange;
    }

    /**
     * @param beansRange the beansRange to set
     */
    public void setBeansRange(int beansRange) {
        this.beansRange = beansRange;
    }

    /**
     * @return the canSetBeansNum
     */
    public boolean isCanSetBeansNum() {
        return canSetBeansNum;
    }

    /**
     * @param canSetBeansNum the canSetBeansNum to set
     */
    public void setCanSetBeansNum(boolean canSetBeansNum) {
        this.canSetBeansNum = canSetBeansNum;
    }

    public boolean haveGM() {
        return gmLevel >= 2 && gmLevel <= 3;
    }

    public void setprefix(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    public final boolean canHold(final int itemid) {
        return getInventory(GameConstants.getInventoryType(itemid)).getNextFreeSlot() > -1;
    }

    public void gainItem(int code, int amount) {
        MapleInventoryManipulator.addById(client, code, (short) amount, null);
    }

    public void gainItem(int code) {
        MapleInventoryManipulator.addById(client, code, (short) 1, null);
    }

    public void giftMedal(int id) {
        if (!this.getInventory(MapleInventoryType.EQUIP).isFull() && this.getInventory(MapleInventoryType.EQUIP).countById(id) == 0 && this.getInventory(MapleInventoryType.EQUIPPED).countById(id) == 0) {
            MapleInventoryManipulator.addById(client, id, (short) 1);
            World.Broadcast.broadcastMessage(MaplePacketCreator.getItemNotice("[恭喜]" + getName() + "剛才得到了 " + MapleItemInformationProvider.getInstance().getName(id) + "！").getBytes());
        } else if (this.getInventory(MapleInventoryType.EQUIP).countById(id) == 0 && this.getInventory(MapleInventoryType.EQUIPPED).countById(id) == 0) {
            MapleInventoryManipulator.drop(client, MapleInventoryType.EQUIP, (byte) 1, (byte) 1);
            MapleInventoryManipulator.addById(client, id, (short) 1);
            World.Broadcast.broadcastMessage(MaplePacketCreator.getItemNotice("[恭喜]" + getName() + "剛才得到了 " + MapleItemInformationProvider.getInstance().getName(id) + "！").getBytes());
        }
    }

    public long getGiveCSpointsLasttime() {
        return this.giveCSpointsLasttime;
    }

    public void autoGiveCSPoints() {

        long current = System.currentTimeMillis();
        if (this.giveCSpointsLasttime == 0) {
            giveCSpointsLasttime = current;
        } else if (current - giveCSpointsLasttime > ServerConstants.CSPOINTS_PERIOD) {
            if (this.getClient() != null) {
                try {
                    int gainPoints = Math.abs(Randomizer.nextInt()) % 10 + 1;
                    this.modifyCSPoints(1, gainPoints);
                    this.dropMessage("在線獎勵 : " + gainPoints + "點Gash");
                } catch (Exception ex) {
                    FilePrinter.printError("AutoCSpoints.txt", ex, "在線商城點數泡點失敗 : ");
                }
            }
            giveCSpointsLasttime = current;
        }

    }

    public void setPlayerVariable(String name, String value) {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT * FROM player_variables WHERE name = ? AND characterid = ?");
            ps.setString(1, name);
            ps.setInt(2, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ps.close();
                ps = con.prepareStatement("UPDATE player_variables SET value = ? WHERE characterid = ? AND name = ?");
                ps.setString(1, value);
                ps.setInt(2, id);
                ps.setString(3, name);
            } else {
                ps = con.prepareStatement("INSERT INTO player_variables (characterid, name, value) VALUES (?, ?, ?)");
                ps.setInt(1, id);
                ps.setString(2, name);
                ps.setString(3, value);
            }
            ps.close();
            rs.close();
        } catch (SQLException ex) {
            System.out.println("Error setting player variable: " + ex);
        }
    }

    public String getPlayerVariable(String name) {
        try {
            PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement("SELECT * FROM player_variables WHERE name = ? AND characterid = ?");
            ps.setString(1, name);
            ps.setInt(2, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String value = rs.getString("value");
                ps.close();
                rs.close();
                return value;
            } else {
                ps.close();
                rs.close();
                return null;
            }
        } catch (SQLException ex) {
            System.out.println("Error getting player variable: " + ex);
            return null;
        }
    }

    public void deletePlayerVariable(String name) {
        try {
            PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement("SELECT * FROM player_variables WHERE name = ? AND characterid = ?");
            ps.setString(1, name);
            ps.setInt(2, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ps.close();
                rs.close();
                ps = DatabaseConnection.getConnection().prepareStatement("DELETE FROM player_variables WHERE name = ? AND characterid = ?");
                ps.setString(1, name);
                ps.setInt(2, id);
                ps.execute();
            }
            ps.close();
            rs.close();
        } catch (SQLException ex) {
            System.out.println("Error deleting player variable: " + ex);
        }
    }

    public void disposeSchedules() {
        if (BerserkSchedule != null) {
            BerserkSchedule.cancel(false);
            BerserkSchedule = null;
        }
        if (dragonBloodSchedule != null) {
            dragonBloodSchedule.cancel(false);
            dragonBloodSchedule = null;
        }
        if (fairySchedule != null) {
            fairySchedule.cancel(false);
            fairySchedule = null;
        }
        if (mapTimeLimitTask != null) {
            mapTimeLimitTask.cancel(false);
            mapTimeLimitTask = null;
        }
        if (fishing != null) {
            fishing.cancel(false);
            fishing = null;
        }
    }

    public void showInfo(String caption, boolean pink, String msg) {
        short type = (short) (pink ? 5 : -5);
        if (caption != null && !caption.isEmpty()) {
            msg = "[" + caption + "] " + msg;
        }
        dropMessage(type, msg);
        dropMessage(-1, msg);
    }

    public int addAntiMacroFailureTimes() {
        int times = getAntiMacroFailureTimes();
        setAntiMacroFailureTimes(++times);
        return times;
    }

    public void setAntiMacroFailureTimes(int times) {
        updateOneInfo(99999, "AntiMacroWorning", String.valueOf(times));
    }

    public int getAntiMacroFailureTimes() {
        String timesStr = getOneInfo(99999, "AntiMacroWorning");
        if (timesStr == null) {
            setAntiMacroFailureTimes(0);
            return 0;
        }
        return Integer.parseInt(timesStr);
    }

    public ReentrantLock getQuestLock() {
        return questLock;
    }

}

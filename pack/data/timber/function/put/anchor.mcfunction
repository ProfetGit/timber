# the cell the displays stand in (the log right above the cut when the tree hangs) is never put back: a display is lit
# by the cell its entity stands in, so a block put back there drew the whole tree black
execute align xyz run summon marker ~ ~ ~ {Tags:["timber.anc"]}
execute store result score #awx timber.data run data get entity @e[type=marker,tag=timber.anc,limit=1] Pos[0] 1000
execute store result score #awy timber.data run data get entity @e[type=marker,tag=timber.anc,limit=1] Pos[1] 1000
execute store result score #awz timber.data run data get entity @e[type=marker,tag=timber.anc,limit=1] Pos[2] 1000
kill @e[type=marker,tag=timber.anc]
scoreboard players operation #awx timber.data -= #px timber.data
scoreboard players operation #awy timber.data -= #py timber.data
scoreboard players operation #awz timber.data -= #pz timber.data

data modify storage timber:op cd set value {}
data modify storage timber:op cd.drops set from storage timber:op drops
data modify storage timber:op cd.leaf set from storage timber:op seen
data modify storage timber:op cd.log set from storage timber:op t.log
data modify storage timber:op cd.yaw set from storage timber:op s.yaw
data modify storage timber:op cd.qsi set from storage timber:op s.qsi
data modify storage timber:op cd.qci set from storage timber:op s.qci
execute store result storage timber:op cd.lz double 0.001 run scoreboard players get #lz timber.data
execute store result storage timber:op cd.cw double 0.001 run scoreboard players get #cw timber.data
execute store result storage timber:op cd.rs int 1 run scoreboard players get #rs timber.data
data modify storage timber:op cd.owner set from entity @a[tag=timber.cutter,limit=1] UUID

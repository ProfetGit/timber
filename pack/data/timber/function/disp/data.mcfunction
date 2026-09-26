data modify storage timber:op cd set value {}
data modify storage timber:op cd.drops set from storage timber:op drops
data modify storage timber:op cd.ldrops set from storage timber:op ldrops
data modify storage timber:op cd.leaf set from storage timber:op seen
data modify storage timber:op cd.log set from storage timber:op t.log
data modify storage timber:op cd.yaw set from storage timber:op s.yaw
data modify storage timber:op cd.qsi set from storage timber:op s.qsi
data modify storage timber:op cd.qci set from storage timber:op s.qci
execute store result storage timber:op cd.lz double 0.001 run scoreboard players get #lz timber.data
execute store result storage timber:op cd.cw double 0.001 run scoreboard players get #cw timber.data
execute store result storage timber:op cd.rs int 1 run scoreboard players get #rs timber.data
scoreboard players operation #a timber.data = #cos timber.data
scoreboard players operation #a timber.data += #sin timber.data
execute store result storage timber:op cd.pc int 1 run scoreboard players get #a timber.data
scoreboard players operation #a timber.data = #cos timber.data
scoreboard players operation #a timber.data -= #sin timber.data
execute store result storage timber:op cd.mc int 1 run scoreboard players get #a timber.data
scoreboard players operation #a timber.data = #hang timber.data
scoreboard players operation #a timber.data /= #1000 timber.data
scoreboard players operation #a timber.data += #by timber.data
execute store result storage timber:op cd.ky int 1 run scoreboard players get #a timber.data
data modify storage timber:op cd.owner set from entity @a[tag=timber.cutter,limit=1] UUID
scoreboard players operation #a timber.data = #rs timber.data
scoreboard players operation #a timber.data += #h timber.data
scoreboard players operation #a timber.data *= #500 timber.data
execute store result storage timber:op cd.cy double 0.001 run scoreboard players get #a timber.data
scoreboard players operation #a timber.data = #cw timber.data
scoreboard players operation #a timber.data *= #9 timber.data
scoreboard players operation #a timber.data /= #20 timber.data
execute store result storage timber:op cd.cr double 0.001 run scoreboard players get #a timber.data
execute store result storage timber:op cd.hy double 0.001 run scoreboard players get #hang timber.data
function timber:disp/leaf_fx

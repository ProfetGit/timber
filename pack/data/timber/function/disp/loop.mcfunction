execute unless data storage timber:op recs[0] run return 0
execute if score #budget timber.data matches ..0 run return 0
scoreboard players remove #budget timber.data 1
execute store result score #wx timber.data run data get storage timber:op recs[0].x 1000
execute store result score #wy timber.data run data get storage timber:op recs[0].y 1000
execute store result score #wz timber.data run data get storage timber:op recs[0].z 1000
scoreboard players operation #wx timber.data -= #px timber.data
scoreboard players operation #wy timber.data -= #py timber.data
scoreboard players operation #wz timber.data -= #pz timber.data
scoreboard players operation #wy timber.data -= #hang timber.data
scoreboard players operation #k timber.data = #wy timber.data
scoreboard players add #k timber.data 500
scoreboard players operation #k timber.data /= #1000 timber.data
scoreboard players operation #kn timber.data < #k timber.data
scoreboard players operation #kt timber.data > #k timber.data
scoreboard players operation #tx timber.data = #wx timber.data
scoreboard players operation #tx timber.data *= #cos timber.data
scoreboard players operation #a timber.data = #wz timber.data
scoreboard players operation #a timber.data *= #sin timber.data
scoreboard players operation #tx timber.data += #a timber.data
scoreboard players operation #tx timber.data /= #10000 timber.data
scoreboard players operation #tz timber.data = #wz timber.data
scoreboard players operation #tz timber.data *= #cos timber.data
scoreboard players operation #a timber.data = #wx timber.data
scoreboard players operation #a timber.data *= #sin timber.data
scoreboard players operation #tz timber.data -= #a timber.data
scoreboard players operation #tz timber.data /= #10000 timber.data
execute store result storage timber:op d.transformation.translation[0] float 0.001 run scoreboard players get #tx timber.data
execute store result storage timber:op d.transformation.translation[1] float 0.001 run scoreboard players get #wy timber.data
execute store result storage timber:op d.transformation.translation[2] float 0.001 run scoreboard players get #tz timber.data
function timber:compat/block_state
data modify storage timber:op d.Tags[2] set value "timber.lg"
execute if data storage timber:op recs[0].l run data modify storage timber:op d.Tags[2] set value "timber.lf"
execute unless data storage timber:op recs[0].l run scoreboard players operation #km timber.data > #k timber.data
execute if score #hang timber.data matches 0 unless data storage timber:op recs[0].h summon block_display run function timber:disp/init
execute if score #hang timber.data matches 1000 unless data storage timber:op recs[0].h positioned ~ ~1 ~ summon block_display run function timber:disp/init
execute if data storage timber:op recs[0].l run function timber:disp/leaf_width
data remove storage timber:op recs[0]
function timber:disp/loop

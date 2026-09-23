data modify storage timber:op recs[-1].n set value "minecraft:pale_hanging_moss"
execute if block ~ ~ ~ minecraft:pale_hanging_moss[tip=true] run data modify storage timber:op recs[-1].p.tip set value "true"

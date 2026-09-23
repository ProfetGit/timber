scoreboard players add #blocks timber.data 1
scoreboard players operation #cx timber.data = @s timber.x
scoreboard players operation #cy timber.data = @s timber.y
scoreboard players operation #cz timber.data = @s timber.z
execute if score #cy timber.data > #ymax timber.data run scoreboard players operation #ymax timber.data = #cy timber.data
function timber:decor/around
function timber:rec/new
$data modify storage timber:op recs[-1].n set value "$(log)"
data modify storage timber:op recs[-1].p.axis set value "y"
$execute if block ~ ~ ~ $(wood)[axis=x] run data modify storage timber:op recs[-1].p.axis set value "x"
$execute if block ~ ~ ~ $(wood)[axis=z] run data modify storage timber:op recs[-1].p.axis set value "z"
data modify storage timber:op logs append from storage timber:op recs[-1]
function timber:job/restore with storage timber:op t
function timber:drops/loot
setblock ~ ~ ~ air

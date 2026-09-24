tag @s remove timber.new
scoreboard players set @s timber.ph 0
scoreboard players set @s timber.t 0
scoreboard players set @s timber.p 0
scoreboard players operation @s timber.hit = #hit timber.data
scoreboard players operation @s timber.dur = #dur timber.data
scoreboard players operation @s timber.h = #h timber.data
scoreboard players operation @s timber.b = #amp timber.data
scoreboard players operation @s timber.e = #tilt timber.data
scoreboard players operation @s timber.dr = #drop timber.data
scoreboard players operation @s timber.km = #km timber.data
execute if score #hang timber.data matches 1000 run tag @s add timber.hang
function timber:disp/data
data modify entity @s data set from storage timber:op cd
playsound minecraft:block.wood.break block @a ~ ~ ~ 1 0.6
particle minecraft:small_gust ~ ~0.5 ~ 0.25 0.2 0.25 0 1
execute if score #feedback timber.config matches 1 run title @a[tag=timber.cutter,limit=1] actionbar [{text:"🪓 ",color:"gold"},{text:"Timber! ",color:"gold",bold:true},{score:{name:"#n",objective:"timber.data"},color:"white"},{text:" logs",color:"gray"}]
tag @a remove timber.cutter

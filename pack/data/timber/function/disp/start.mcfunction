tag @s remove timber.new
scoreboard players set @s timber.ph 0
scoreboard players set @s timber.t 0
scoreboard players set @s timber.p 0
scoreboard players operation @s timber.hit = #hit timber.data
scoreboard players operation @s timber.dur = #dur timber.data
scoreboard players operation @s timber.h = #h timber.data
scoreboard players operation @s timber.b = #amp timber.data
scoreboard players operation @s timber.dr = #drop timber.data
scoreboard players operation @s timber.km = #km timber.data
execute if score #hang timber.data matches 1000 run tag @s add timber.hang
scoreboard players set @s timber.qs 0
scoreboard players set @s timber.xl 0
scoreboard players set @s timber.bn 0
scoreboard players set @s timber.bv 0
scoreboard players set @s timber.bl 0
scoreboard players set @s timber.pv 0
scoreboard players set @s timber.wv 0
scoreboard players set @s timber.gx 1000
scoreboard players set @s timber.gy 1000
scoreboard players set @s timber.gz 1000
scoreboard players set @s timber.lx 1000
scoreboard players set @s timber.lz 1000
scoreboard players operation @s timber.kn = #kn timber.data
scoreboard players operation @s timber.kt = #kt timber.data
tag @s add timber.flex
tag @s add timber.shiver
function timber:disp/data
data modify entity @s data set from storage timber:op cd
playsound minecraft:block.wood.break block @a ~ ~ ~ 1 0.6
particle minecraft:small_gust ~ ~0.5 ~ 0.25 0.2 0.25 0 1
function timber:anim/fx/shake with storage timber:op cd
execute if score #feedback timber.config matches 1 run title @a[tag=timber.cutter,limit=1] actionbar [{text:"🪓 ",color:"gold"},{text:"Timber! ",color:"gold",bold:true},{score:{name:"#n",objective:"timber.data"},color:"white"},{text:" logs",color:"gray"}]
tag @a remove timber.cutter

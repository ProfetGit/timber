fillbiome -30 -64 -30 70 30 70 minecraft:old_growth_spruce_taiga
fill -2 -61 -2 19 -61 19 minecraft:podzol
execute positioned 8 -60 8 run function tscene:tree/spruce_b
time set 5000
weather clear
kill @e[type=item]
data merge storage tscene:cfg {t:"spruce",cx:8,cy:-60,cz:8,fx:5.5,fy:-60,fz:9.0,fyaw:-90}
data modify storage tscene:cfg fp set value {x:5.5,y:-60,z:9.0,yaw:-90,pitch:15}
data modify storage tscene:cfg side set value {x:22.5,y:-58,z:42.5,yaw:180,pitch:-12}

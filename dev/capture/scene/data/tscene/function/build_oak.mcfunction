fillbiome -30 -64 -30 60 0 60 minecraft:plains
execute positioned 8 -60 8 run function tscene:tree/oak_c
time set 6000
weather clear
kill @e[type=item]
data merge storage tscene:cfg {t:"oak",cx:8,cy:-60,cz:8,fx:5.5,fy:-60,fz:8.5,fyaw:-90}
data modify storage tscene:cfg fp set value {x:5.5,y:-60,z:8.5,yaw:-90,pitch:20}
data modify storage tscene:cfg side set value {x:11.5,y:-59,z:19.5,yaw:180,pitch:-5}

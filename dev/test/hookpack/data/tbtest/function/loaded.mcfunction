scoreboard objectives add tbtest dummy
scoreboard players add #loaded tbtest 1
execute store result score #version_id tbtest run data get storage timber:meta version_id
data modify storage timber:meta requires append value {text:"Test requirement. ",color:"gray"}

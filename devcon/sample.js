//CSVTOJSON

// @ts-nocheck

var request = require("request")
var async = require("async")
var fs = require("fs");
var path = require("path");
var csvjson = require('csvjson');
var data = fs.readFileSync(path.join(__dirname, 'Data-Student-Parent-Teacher.csv'), { encoding : 'utf8'});
console.log("Data",data);
/*
{
    delimiter : <String> optional default is ","
    quote     : <String|Boolean> default is null
}
*/
var options = {
  delimiter : ',', // optional
  quote     : '"' // optional
};
// for multiple delimiter you can use regex pattern like this /[,|;]+/
 
/* 
  for importing headers from different source you can use headers property in options 
  var options = {
    headers : "sr,name,age,gender"
  };
*/

const jsonObject = csvjson.toObject(data, options);
console.log("JSON Object",jsonObject);
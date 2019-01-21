/**
 * Programmatically invoke Open-Saber APIs to populat devcon database
 * and spit out "code -> osid" map
 */

// @ts-check
var request = require("request")
var async = require("async")
var fs = require("fs");
var path = require("path");
var csvjson = require('csvjson');

var baseUrl = "http://localhost:8080"
var PARALLEL_LIMIT = 1;
var g_tasks = []

var teacherEntities = {}
var studentEntities = {}
var parentEntities = {}
var stallEntities = {}

var invoke_add = function (nIter, payload, callback) {
    var addSuffix = "add"
    var url = baseUrl + "/" + addSuffix
    var headerVars = {
        "Content-Type": "application/json"
    }

    console.log("#" + nIter + " Invoking " + url + " with payload " + payload)
    request(url, {
        method: "POST",
        body: payload,
        headers: headerVars
    }, function (err, response, body) {
        console.log(JSON.stringify(body))
        var result = JSON.parse(body)["result"]
        if (err) {
            console.error(err)
            callback(err)
        } else {
            callback(null, result)
        }
    })
}

/**
 * Merges two json entities
 * @param {*} entityType 
 * @param {*} one static payload
 * @param {*} two one row you've read from CSV
 */
var merge_json = function (entityType, one, two) {
    if (entityType === undefined ||
        one === undefined ||
        two === undefined) {
        return null
    }
    //console.log(JSON.stringify(two))
    var attrsMerged = Object.assign(one["request"][entityType], two)
    var entityTypeMerged = one
    entityTypeMerged["request"][entityType] = attrsMerged
    //console.log("Merged = " + JSON.stringify(entityTypeMerged))
    return entityTypeMerged
}


var addToArr = function (arr, val, cb) {
    arr.push(val)
    cb()
}

var allPayloads = []
/**
 * 
 */
var populate_add_tasks = function (entityType, static_payload, arrDynamicData) {

    for (var itr = 0; itr < arrDynamicData.length; itr++) {
        //async.eachSeries(arrDynamicData, function (oneCSVRow, callback) {
        var completePayload = JSON.parse(JSON.stringify(static_payload))
        var oneCSVRow = JSON.parse(JSON.stringify(arrDynamicData[itr]))

        console.log("one row = " + JSON.stringify(oneCSVRow))

        var attrsMerged = Object.assign(completePayload["request"][entityType], oneCSVRow)
        completePayload["request"][entityType] = attrsMerged

        console.log(itr + " - payload = " + JSON.stringify(completePayload))

        var dataPortion = completePayload["request"][entityType]
        for (var field in dataPortion) {
            var fieldVal = dataPortion[field]
            if (fieldVal.indexOf("[") != -1) {
                if (fieldVal.indexOf(",") != -1) {
                } else {
                    var myArr = new Array()
                    var subj = fieldVal.replace(/\[|\]/g, "")
                    console.log("Just one item in the array for " + field + " = " + subj)

                    if (parseInt(subj)) {
                        console.log("is integer")
                        myArr.push(parseInt(subj))
                    } else {
                        myArr.push(subj)
                    }
                    dataPortion[field] = myArr
                }
            } 
            if (field === 'phone') {
                var phone = parseInt(dataPortion[field]) * 100
                dataPortion[field] = phone + (itr + 1)
            }
        }
        allPayloads.push(completePayload)
    }

    //console.log("Lengths of tasks = " + arrDynamicData.length + " and " + allPayloads.length)
    //console.log(JSON.stringify(allPayloads))

    async.forEachOf(allPayloads, function (onePayload, nIter, callback) {
        g_tasks.push(
            (cb) => invoke_add(nIter, JSON.stringify(onePayload), function (err, data) {
                var code = onePayload["request"][entityType]["code"]
                teacherEntities[code] = data[entityType]["osid"]
                console.log("At the end of " + nIter + " -> " + JSON.stringify(teacherEntities))
                cb()
            })
        )
        callback()
    })
}

/**
 * Executes all the populated tasks in parallel.
 */
var execute_tasks = function (fileName, entities) {
    async.parallelLimit(g_tasks, PARALLEL_LIMIT, function (err, callback) {
        if (!err) {
            console.log("entities " + JSON.stringify(entities))
            fs.writeFile(fileName, JSON.stringify(entities), null, function (err) {
                if (err) {
                    console.error("FATAL : Error in writing teacher entity " + err)
                }
            })
        } else {
            console.error(err)
            console.log("One or more errors occurred.")
        }
    })


}

var options = {
    delimiter: ',', // optional
    quote: '"' // optional
};

var csvToJson = function (csvFileName) {
    var data = fs.readFileSync(path.join(__dirname, csvFileName), { encoding: 'utf8' });
    const jsonObject = csvjson.toObject(data, options);
    //console.log("JSON Object", jsonObject);
    return jsonObject;
}

var teacherPayload = {
    "id": "open-saber.registry.create",
    "request": {
        "Teacher": {
            "board": "CBSE",
            "medium": "English",
            "district": "Bangalore",
            "state": "Karnataka"
        }
    }
}

var teacherCSV = csvToJson('Data - Teacher.csv')
populate_add_tasks("Teacher", teacherPayload, teacherCSV)
console.log("Total number of tasks = " + g_tasks.length)
execute_tasks("Teacher_entity.json", teacherEntities)


/*
// Test for replacement 
var s = "[EVS]"
console.log(s.replace(/\[|\]/g, ""))
*/

/*
// Sample for merging two json objects
var one = {
    "id": "something",
    "request": {
        "entityType": {
            "a": 1
        }
    }
}

var two = {
    "b": 2
}

var payload = Object.assign(one.request["entityType"], two)
console.log(JSON.stringify(payload))
one.request = payload
console.log(JSON.stringify(one))
*/


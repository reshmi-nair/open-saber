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

var baseUrl = "http://10.0.1.193:8080"
var PARALLEL_LIMIT = 1;
var teacherEntities = {}
var studentEntities = {}
var parentEntities = {}
var stallEntities = {}
var visitorEntities = {}

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
            console.log(" error for " + payload)
            callback(err)
        } else {
            callback(null, result)
            console.log(" success for " + payload)
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

/**
 * 
 */
var populate_add_tasks = function (tasks, entityType, static_payload, arrDynamicData, someEntity) {
    var allPayloads = []

    for (var itr = 0; itr < arrDynamicData.length; itr++) {
        //async.eachSeries(arrDynamicData, function (oneCSVRow, callback) {
        var completePayload = JSON.parse(JSON.stringify(static_payload))
        var oneCSVRow = JSON.parse(JSON.stringify(arrDynamicData[itr]))
        console.log("PAYLOAD COmplete",JSON.stringify(static_payload))
        console.log("one row = " + JSON.stringify(oneCSVRow))

        var attrsMerged = Object.assign(completePayload["request"][entityType], oneCSVRow)
        completePayload["request"][entityType] = attrsMerged

        //console.log(itr + " - payload = " + JSON.stringify(completePayload))

        var dataPortion = completePayload["request"][entityType]
        for (var field in dataPortion) {
            var fieldVal = dataPortion[field]
            if (fieldVal.indexOf("[") != -1) {
                var myArr = new Array()
                var individualItems = fieldVal.replace(/\[|\]/g, "")
                //console.log("Expect [] to be removed " + JSON.stringify(individualItems) + " flag = " + individualItems.indexOf(","));
                if (individualItems.indexOf(",") != -1) {
                    console.log("Array contains multiple values")
                    // More than one item
                    var arrItems = individualItems.split(",")
                    arrItems.forEach(element => {
                        myArr.push(element);
                    });
                    console.log("Array", myArr);
                } else {

                    console.log("Just one item in the array for " + field + " = " + individualItems)

                    if (parseInt(individualItems)) {
                        //console.log("is integer")
                        myArr.push(parseInt(individualItems))
                    } else {
                        myArr.push(individualItems)
                    }
                }
                dataPortion[field] = myArr
            }
            if (field === 'phone') {
                var phone = parseInt(dataPortion[field]) * 100
                dataPortion[field] = phone + (itr + 1)
            }
        }
        delete dataPortion.ParentCode
        delete dataPortion.Subject
        delete dataPortion.ActualTeacher
        delete dataPortion.ActualParent
        delete dataPortion.FetchedCode
       // delete dataPortion.ideaDescription

        allPayloads.push(completePayload)
    }

    //console.log("Lengths of tasks = " + arrDynamicData.length + " and " + allPayloads.length)
    //console.log(JSON.stringify(allPayloads))

    async.forEachOf(allPayloads, function (onePayload, nIter, callback) {
        tasks.push(
            (cb) => invoke_add(nIter, JSON.stringify(onePayload), function (err, data) {
                if (!err) {
                    console.log("data", data);
                    var code = data[entityType]["code"]
                    console.log("__LINE___" + JSON.stringify(onePayload));
                    someEntity[code] = data[entityType]["osid"]
                    console.log("At the end of " + nIter + " -> " + JSON.stringify(someEntity))
                    // parentEntities[code] = data[entityType]["osid"]
                    // console.log("At the end of " + nIter + " -> " + JSON.stringify(parentEntities))
                }
                cb()
            })
        )
        callback()
    })
}

/**
 * Executes all the populated tasks in parallel.
 */
var execute_tasks = function (tasks, fileName, entities, cb) {
    //async.parallelLimit(tasks, PARALLEL_LIMIT, function (err, callback) {
    async.series(tasks, function (err, callback) {
        if (!err) {
            console.log("entities " + JSON.stringify(entities))
            fs.writeFile(fileName, JSON.stringify(entities), null, function (err) {
                if (err) {
                    console.error("FATAL : Error in writing teacher entity " + err)
                    cb(err)
                }
                else
                cb(null);
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

var studentPayload = {
    "id": "open-saber.registry.create",
    "request": {
        "Student": {
        }
    }
}

var parentPayload = {
    "id": "open-saber.registry.create",
    "request": {
        "Parent": {
        }
    }
}
var visitorPayload = {
    "id": "open-saber.registry.create",
    "request": {
        "Visitor": {
        }
    }
}
var stallPayload = {
    "id": "open-saber.registry.create",
    "request": {
        "Stall": {
        }
    }
}

function populateTeacher(cb) {
    var teacher_tasks = []
    var teacherCSV = csvToJson('Data - Teacher.csv')
    populate_add_tasks(teacher_tasks, "Teacher", teacherPayload, teacherCSV, teacherEntities)
    console.log("Total number of teachers = " + teacher_tasks.length)
    execute_tasks(teacher_tasks, "Teacher_entity.json", teacherEntities, cb)
}

function populateStudent(cb) {
    var student_tasks = [];
    var studentCSV = csvToJson('Data - Student-Parent-Teacher.csv')
    populate_add_tasks(student_tasks, "Student", studentPayload, studentCSV, studentEntities)
    console.log("Total number of students = " + student_tasks.length)
    execute_tasks(student_tasks, "Student_entity.json", studentEntities, cb)
}

function populateParent(cb) {
    var parent_tasks = [];
    var parentCSV = csvToJson('Data - Parent-Child.csv')
    populate_add_tasks(parent_tasks, "Parent", parentPayload, parentCSV, parentEntities)
    console.log("Total number of parents = " + parent_tasks.length)
    execute_tasks(parent_tasks, "Parent_entity.json", parentEntities, cb)
}

function populateVisitor(cb) {
    var visitor_tasks = [];
    var visitorCSV = csvToJson('Data - Visitor.csv')
    populate_add_tasks(visitor_tasks, "Visitor", visitorPayload, visitorCSV, visitorEntities)
    console.log("Total number of Visitor = " + visitor_tasks.length)
    execute_tasks(visitor_tasks, "Visitor_entity.json", visitorEntities, cb)
}

function getOneIdea(oneLine) {
    var newIdea = {}
                newIdea.code = oneLine["ideaCode"]
                newIdea.name = oneLine["ideaName"]
                newIdea.description = oneLine["ideaDescription"]
                return newIdea
}

function populateStallIdeas(cb) {
    var stall_tasks = [];
    var combinedInfo = []
    var uniqueStalls = {}
    var stallCSV = csvToJson('Data - Stall-Ideas.csv')
    console.log(JSON.stringify(stallCSV))

    // stallCSV contains multiple 
    if (Array.isArray(stallCSV)) {
        for (var idx in stallCSV) {
          
            console.log(JSON.stringify(stallCSV[idx]["code"]))

            var isAlreadyPresent = uniqueStalls.hasOwnProperty(stallCSV[idx]["code"])
            if (isAlreadyPresent) {
                console.log(idx["code"] + " already present")
                var ideasArr = combinedInfo[uniqueStalls[stallCSV[idx]["code"]]].ideas
                console.log("IdeasArr Before Pushing",ideasArr)
                var newIdea = getOneIdea(stallCSV[idx])
                ideasArr.push(newIdea)
                console.log("IdeasArr",ideasArr)
            } else {
                var ideaArr = []
                var idxToAdd = combinedInfo.length

                var newIdea = getOneIdea(stallCSV[idx])
                ideaArr.push(newIdea)
                var stallObj = {}
                stallObj.code = stallCSV[idx]["code"]
                stallObj.name = stallCSV[idx]["name"]
                stallObj.floor = stallCSV[idx]["floor"]
                stallObj.ideas = ideaArr
                
                console.log("Adding " + stallCSV[idx]["code"] + " into uniqueStalls")
                uniqueStalls[stallCSV[idx]["code"]] = idxToAdd
                combinedInfo.push(stallObj)
            }
        }
    }
    console.log(JSON.stringify(combinedInfo))
    populate_add_tasks(stall_tasks, "Stall", stallPayload, combinedInfo, stallEntities)
    console.log("Total number of Stall = " + stall_tasks.length)
    execute_tasks(stall_tasks, "Stall_entity.json", stallEntities, cb)
}


// populateVisitor(function(err, data) {
//     if (err) {
//         console.log("some error man")
//     }
// })

var instance1_url = "http://10.0.1.193:8080"
var instance2_url = "http://10.0.1.193:8081"
var instance1_setup_functions = [populateStallIdeas, populateVisitor]
var instance2_setup_functions = [populateTeacher,populateStudent,populateParent]


// Driver - change this instance url depending on the need
var whatToHit = baseUrl
var func_array = null
console.log("INSATNSCE1_SETUP",instance1_setup_functions)
// Execution phase
baseUrl = whatToHit
if (baseUrl === instance1_url) {
    func_array = instance1_setup_functions
} else if(baseUrl === instance2_url){
    func_array = instance2_setup_functions
}

//console.log("Func_Array",func_array);

async.series(func_array, (result, err) => {
    console.log("errror = ", err);
    console.log("Results", result);
    if (err) {
        return (err);
        console.log("Errorrrrr==>", err);
    }
    return result;
    console.log("result", result);
})

// async.series([populateVisitor,populateStallIdeas], (result, err) => {
//         console.log("errror = ", err);
//         console.log("Results", result);
//         if (err) {
//             return (err);
//             console.log("Errorrrrr==>", err);
//         }
//         return result;
//         console.log("result", result);
//     })



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


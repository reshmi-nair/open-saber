/**
 * Programmatically invoke Open-Saber APIs to detect any perf problem.
 */

// @ts-check
var request = require("request")
var async = require("async")

const TEACHER_NAME = "_TEACHER_NAME_"

// Replace _TEACHER_NAME_
var payload_sample = {
    "id": "open-saber.registry.create",
    "ver": "1.0",
    "ets": "11234",
    "params": {
        "did": "",
        "key": "",
        "msgid": ""
    },
    "request": {
        "Teacher": {
            "name": "_TEACHER_NAME_",
            "schoolName": "QSBB higher secondary school",
            "board": "CBSE",
            "medium": "English",
            "district": "Bangalore",
            "state": "Karnataka",
            "phone": 9123456780,
            "email": "Ram@devon2019.com",
            "grade": [3],
            "subjects": [
                "Geography"
            ]
        }
    }
}

var baseUrl = "http://localhost:8080"
var PARALLEL_LIMIT = 1;
var USER_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJ1WXhXdE4tZzRfMld5MG5PS1ZoaE5hU0gtM2lSSjdXU25ibFlwVVU0TFRrIn0.eyJqdGkiOiIzNGVhZDAxZi1kZGU3LTQ5ZGMtODk0ZS1kZGY0YTU0ODRiOWIiLCJleHAiOjE1MzM2NDAxNTUsIm5iZiI6MCwiaWF0IjoxNTMzNjIyMTU1LCJpc3MiOiJodHRwczovL2Rldi5vcGVuLXN1bmJpcmQub3JnL2F1dGgvcmVhbG1zL3N1bmJpcmQiLCJhdWQiOiJhZG1pbi1jbGkiLCJzdWIiOiI4NzRlZDhhNS03ODJlLTRmNmMtOGYzNi1lMDI4ODQ1NTkwMWUiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJhZG1pbi1jbGkiLCJhdXRoX3RpbWUiOjAsInNlc3Npb25fc3RhdGUiOiI1MjNiMjllNi01NTJiLTQ1MGItODc0Ny05Y2Y5YmVmMWFjMjgiLCJhY3IiOiIxIiwiYWxsb3dlZC1vcmlnaW5zIjpbXSwicmVzb3VyY2VfYWNjZXNzIjp7fSwibmFtZSI6IkNyZXRhdGlvbiBVc2VyIE5ldyIsInByZWZlcnJlZF91c2VybmFtZSI6Im50cHRlc3QxMDIiLCJnaXZlbl9uYW1lIjoiQ3JldGF0aW9uIiwiZmFtaWx5X25hbWUiOiJVc2VyIE5ldyIsImVtYWlsIjoidXNlcnRlc3QxMkB0ZXN0c3MuY29tIn0.euqYoB-8QrJ2m8_qZHPuZGYNoeJswHxT-HF_usQvSkITwuDknnkZL3otz2eGCj7gKCTgXaaCstAWSwbxupK-RPTEnps-uPER0Fe1R12ZmGO7Q7KMhhnBmtU6jS1dgP65NWbShoQB4Hh7OgBt1y0l2U1L1tJGGhEJrp5lqiAKt_KCzD1tk-2RBRFjTfkRAKYrYQvXXfiXPD_R2n7Mfv3oB9bUI6ccEcMAs5WE-QifFdrCGCui0QWHE_OBbWY9pF0b6dqsobvvPrA5I2rjUIs9QXipm9rWUVFCOuEAx7tZv2zjZwWKjiE1mCJePjCrfGjazn99cEajQBvQRzqN36WcuQ"

var invoke_add = function (nIter, payload, callback) {
    var addSuffix = "add"
    var url = baseUrl + "/" + addSuffix
    var headerVars = {
        "x-authenticated-user-token": USER_TOKEN,
        "Content-Type": "application/json"
    }

    console.log("#" + nIter + " Invoking " + url + " with payload " + payload)
    request(url, {
        method: "POST",
        body: payload,
        headers: headerVars
    }, function (err, response, body) {
        console.log("#" + nIter + " Finished - " + response.statusCode + " - " + JSON.stringify(JSON.parse(body)["result"]))
        if (err) {
            console.error(err)
            callback(err)
        } else {
            callback(null, null)
        }
    })
}

var g_tasks = []

/**
 * 
 * @param {number} numRequests - Number of requests
 // TODO - for now, let it be just on the teachers' name
 */
var populate_add_tasks = function (numRequests) {
    var countArr = []
    for (var itr = 1; itr <= numRequests; itr++) {
        countArr.push(itr)
    }

    async.each(countArr, function (nIter, callback) {
        g_tasks.push(
            (cb) => invoke_add(nIter, JSON.stringify(payload_sample).replace(new RegExp(TEACHER_NAME, "g"), "John" + nIter + ""), function (err, callback2) {
                cb()
            })
        )
        callback()
    })
}

/**
 * Executes all the populated tasks in parallel.
 */
var execute_tasks = function () {
    async.parallelLimit(g_tasks, PARALLEL_LIMIT, function (err, callback) {
        if (!err) {
            console.log("Successfully completed with all requests.")
        } else {
            console.error(err)
            console.log("One or more errors occurred.")
        }
    })
}

populate_add_tasks(2)
console.log("Total number of tasks = " + g_tasks.length)
execute_tasks()


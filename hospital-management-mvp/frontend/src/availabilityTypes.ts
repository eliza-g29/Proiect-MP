export type AvailabilityDay={date:string;workingHours:{startAt:string;endAt:string}[];slots:string[]};
export type DoctorAvailability={doctorId:number;name:string;specialty:string;days:AvailabilityDay[]};

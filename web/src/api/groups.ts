import {apiClient} from './client';
export interface DeviceGroup {id:number;name:string}
export const listGroups=()=>apiClient.get<DeviceGroup[]>('/private/groups/search');
export const saveGroup=(group:{id?:number;name:string})=>apiClient.put('/private/groups',group);
export const assignGroups=(ids:number[],group:DeviceGroup,action:'set'|'remove')=>apiClient.post('/private/devices/groupBulk',{ids,groups:[group],action});
